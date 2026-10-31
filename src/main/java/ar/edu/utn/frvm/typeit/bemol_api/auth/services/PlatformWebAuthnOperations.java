package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformPasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.DuplicatePasskeyCredentialException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore.Ceremony;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import com.webauthn4j.verifier.exception.VerificationException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class PlatformWebAuthnOperations {
  private final PlatformPasskeyRepositoryAdapter repository;
  private final FrontendPublicProperties frontend;
  private final PlatformPasskeyCredentialRepository credentials;
  private final PlatformPasskeyCredentialMapper mapper;
  private final WebAuthnOptionsCodec codec;
  private final PlatformPasskeyStateStore state;
  private final AuditEventRecorder audit;

  public Webauthn4JRelyingPartyOperations get() {
    final var rp =
        PublicKeyCredentialRpEntity.builder()
            .id(frontend.platformUri().getHost())
            .name("Bemol — Administración")
            .build();
    final var operations =
        new Webauthn4JRelyingPartyOperations(
            repository, repository, rp, Set.of(frontend.platformPublicUrl()));
    operations.setCustomizeCreationOptions(
        builder ->
            builder
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .attestation(AttestationConveyancePreference.NONE));
    operations.setCustomizeRequestOptions(
        builder -> builder.userVerification(UserVerificationRequirement.REQUIRED));
    return operations;
  }

  public PasskeyAuthenticationOptionsResponse assertionOptions(
      final PlatformAccount account,
      final String sessionId,
      final String attemptId,
      final String purpose,
      final String target) {
    state.limit(account.getId());
    if (!credentials.existsActiveByPlatformAccountId(account.getId())) {
      throw new WebAuthnVerificationFailedException();
    }
    final var options =
        get()
            .createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                        account.getId().toString(), null)));
    final String snapshot = codec.encodeRequestOptions(options);
    final String id =
        state.storeCeremony(
            new Ceremony(account.getId(), sessionId, attemptId, purpose, target, "", "", snapshot));
    return new PasskeyAuthenticationOptionsResponse(id, codec.parseSnapshot(snapshot));
  }

  public PasskeyRegistrationOptionsResponse creationOptions(
      final PlatformAccount account,
      final String sessionId,
      final String attemptId,
      final String purpose,
      final String grant,
      final String label) {
    final var options =
        get()
            .createPublicKeyCredentialCreationOptions(
                new ImmutablePublicKeyCredentialCreationOptionsRequest(
                    UsernamePasswordAuthenticationToken.authenticated(
                        account.getId().toString(), null, java.util.List.of())));
    final String snapshot = codec.encodeCreationOptions(options);
    final String id =
        state.storeCeremony(
            new Ceremony(
                account.getId(),
                sessionId,
                attemptId,
                purpose,
                "register",
                grant,
                label.trim(),
                snapshot));
    return new PasskeyRegistrationOptionsResponse(id, codec.parseSnapshot(snapshot));
  }

  public void verifyAssertion(
      final Ceremony ceremony, final PlatformAccount account, final JsonNode node) {
    state.limit(account.getId());
    try {
      final var credential = codec.decodeAssertionCredential(node);
      final var owner =
          get()
              .authenticate(
                  new RelyingPartyAuthenticationRequest(
                      codec.decodeRequestOptions(ceremony.options()), credential));
      if (owner == null
          || !Arrays.equals(account.getWebauthnUserHandle(), owner.getId().getBytes())) {
        throw new WebAuthnVerificationFailedException();
      }
      final var stored =
          credentials
              .findByCredentialId(credential.getId())
              .filter(PlatformPasskeyCredential::isActive)
              .orElseThrow(WebAuthnVerificationFailedException::new);
      if (!stored.getPlatformAccount().getId().equals(account.getId())) {
        throw new WebAuthnVerificationFailedException();
      }
    } catch (VerificationException | IllegalArgumentException | IllegalStateException exception) {
      throw new WebAuthnVerificationFailedException();
    }
  }

  public CredentialRecord verifyRegistration(
      final Ceremony ceremony, final PlatformAccount account, final JsonNode node) {
    try {
      final var record =
          get()
              .registerCredential(
                  new ImmutableRelyingPartyRegistrationRequest(
                      codec.decodeCreationOptions(ceremony.options()),
                      new RelyingPartyPublicKey(
                          codec.decodeAttestationCredential(node), ceremony.label())));
      if (!Arrays.equals(
          account.getWebauthnUserHandle(), record.getUserEntityUserId().getBytes())) {
        throw new WebAuthnVerificationFailedException();
      }
      return record;
    } catch (VerificationException | IllegalArgumentException | IllegalStateException exception) {
      throw new WebAuthnVerificationFailedException();
    }
  }

  public PlatformPasskeyCredential saveCredential(
      final PlatformAccount account, final CredentialRecord record, final String label) {
    final var credential = mapper.toEntity(account, record, label);
    try {
      credentials.saveAndFlush(credential);
    } catch (DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "platform_passkey_credentials_credential_id_key"
                .equals(violation.getConstraintName())) {
          throw new DuplicatePasskeyCredentialException();
        }
      }
      throw exception;
    }
    recordChange(account, credential, AuditAction.PASSKEY_CREATED);
    return credential;
  }

  public void recordChange(
      final PlatformAccount account,
      final PlatformPasskeyCredential credential,
      final AuditAction action) {
    audit.recordCurrentActor(
        null,
        null,
        action,
        AuditEntityType.PASSKEY,
        credential.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of("platformAccountId", account.getId().toString()));
  }
}
