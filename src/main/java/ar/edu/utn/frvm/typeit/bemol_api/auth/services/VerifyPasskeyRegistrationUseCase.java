package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.DuplicatePasskeyCredentialException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.LoginStateInconsistentException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyLimitExceededException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnCeremonyInvalidException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import com.webauthn4j.verifier.exception.MaliciousCounterValueException;
import com.webauthn4j.verifier.exception.VerificationException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyPasskeyRegistrationUseCase {
  private final AuditEventRecorder auditRecorder;

  private final UserRepository userRepository;
  private final PasskeyCredentialRepository passkeyCredentialRepository;
  private final PasskeyCredentialMapper mapper;
  private final WebAuthnRelyingPartyOperations relyingPartyOperations;
  private final WebAuthnCeremonyService ceremonyService;
  private final WebAuthnProperties properties;
  private final WebAuthnOptionsCodec optionsCodec;

  @Transactional
  public PasskeyResponse execute(
      final JwtAuthenticatedUser principal,
      final String ceremonyId,
      final JsonNode credentialNode) {
    if (principal.delegated() || principal.accountUserId().isEmpty()) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException();
    }

    final RegistrationCeremony ceremony =
        ceremonyService.consumeRegistration(ceremonyId).orElse(null);
    if (ceremony == null) {
      log.info("[Auth] Passkey registration ceremony missing, userId: {}", principal.userId());
      throw new WebAuthnCeremonyInvalidException();
    }
    final boolean boundToSession =
        ceremony.userId().equals(principal.userId())
            && ceremony.sessionId().equals(principal.sessionId());
    if (!boundToSession) {
      log.info("[Auth] Passkey registration ceremony not bound, userId: {}", principal.userId());
      throw new WebAuthnCeremonyInvalidException();
    }
    final User locked =
        userRepository
            .findWithLockById(principal.userId())
            .orElseThrow(LoginStateInconsistentException::new);
    if (passkeyCredentialRepository.countActiveByUserId(locked.getId())
        >= properties.maxPasskeys()) {
      throw new PasskeyLimitExceededException();
    }
    final PublicKeyCredentialCreationOptions options = readOptions(ceremony.optionsJson());
    final PublicKeyCredential<AuthenticatorAttestationResponse> credential =
        readCredential(credentialNode);
    final CredentialRecord record;
    try {
      record =
          relyingPartyOperations.registerCredential(
              new ImmutableRelyingPartyRegistrationRequest(
                  options, new RelyingPartyPublicKey(credential, ceremony.label())));
    } catch (final MaliciousCounterValueException exception) {
      log.warn("[Auth] Passkey registration counter mismatch, userId: {}", principal.userId());
      throw new WebAuthnVerificationFailedException();
    } catch (final VerificationException | IllegalArgumentException exception) {
      log.info("[Auth] Passkey registration failed, userId: {}", principal.userId());
      throw new WebAuthnVerificationFailedException();
    }
    final String credentialId = record.getCredentialId().toBase64UrlString();
    if (passkeyCredentialRepository.findByCredentialId(credentialId).isPresent()) {
      throw new DuplicatePasskeyCredentialException();
    }
    final PasskeyCredential entity = mapper.toEntity(locked, record, ceremony.label());
    final PasskeyCredential saved;
    try {
      saved = passkeyCredentialRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException exception) {
      if (isCredentialIdConflict(exception)) {
        throw new DuplicatePasskeyCredentialException();
      }
      throw exception;
    }
    auditRecorder.recordCurrentActor(
        locked.getInstitution(),
        principal.personId(),
        AuditAction.PASSKEY_CREATED,
        AuditEntityType.PASSKEY,
        saved.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
    log.info("[Auth] Passkey registered, userId: {}", principal.userId());
    return PasskeyResponse.from(saved);
  }

  private static boolean isCredentialIdConflict(final DataIntegrityViolationException exception) {
    Throwable cause = exception.getCause();
    while (cause != null) {
      if (cause instanceof ConstraintViolationException violation) {
        return "passkey_credentials_credential_id_key".equals(violation.getConstraintName());
      }
      cause = cause.getCause();
    }
    return false;
  }

  private PublicKeyCredentialCreationOptions readOptions(final String json) {
    try {
      return optionsCodec.decodeCreationOptions(json);
    } catch (IllegalStateException exception) {
      throw new WebAuthnCeremonyInvalidException();
    }
  }

  private PublicKeyCredential<AuthenticatorAttestationResponse> readCredential(
      final JsonNode node) {
    try {
      return optionsCodec.decodeAttestationCredential(node);
    } catch (IllegalStateException exception) {
      log.info("[Auth] Passkey registration credential unreadable");
      throw new WebAuthnVerificationFailedException();
    }
  }
}
