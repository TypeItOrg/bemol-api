package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyEmailVerificationRequiredException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnCeremonyInvalidException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import com.webauthn4j.verifier.exception.VerificationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class DiscoverablePasskeyLoginUseCase {
  private final InstitutionalHostContext hostContext;
  private final WebAuthnProperties properties;
  private final InstitutionRepository institutions;
  private final UserRepository users;
  private final PasskeyCredentialRepository credentials;
  private final WebAuthnRelyingPartyOperations relyingPartyOperations;
  private final DiscoverableAuthenticationCeremonyService ceremonies;
  private final WebAuthnOptionsCodec codec;
  private final AuthenticationSessionIssuer sessionIssuer;

  @Transactional(readOnly = true)
  public PasskeyAuthenticationOptionsResponse options(final UUID institutionId) {
    hostContext.requireInstitution(institutionId);
    final var institution =
        institutions.findById(institutionId).orElseThrow(WebAuthnVerificationFailedException::new);
    if (!institution.isActive()) {
      throw new WebAuthnVerificationFailedException();
    }

    final var options =
        relyingPartyOperations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
    final String optionsJson = codec.encodeRequestOptions(options);
    final String ceremonyId =
        ceremonies.store(
            institutionId,
            hostContext.trustedRequestOrigin().orElse(""),
            trustedOrigins(),
            optionsJson);
    return new PasskeyAuthenticationOptionsResponse(ceremonyId, codec.parseSnapshot(optionsJson));
  }

  @Transactional
  public AuthResponse verify(
      final String ceremonyId,
      final JsonNode credentialNode,
      final boolean rememberMe,
      final HttpServletRequest request) {
    final var ceremony =
        ceremonies.consume(ceremonyId).orElseThrow(WebAuthnCeremonyInvalidException::new);
    hostContext.requireInstitution(ceremony.institutionId());
    if (!ceremony.hostOrigin().equals(hostContext.trustedRequestOrigin().orElse(""))
        || !ceremony.trustedOrigins().equals(trustedOrigins())) {
      throw new WebAuthnCeremonyInvalidException();
    }

    final PublicKeyCredentialRequestOptions options;
    try {
      options = codec.decodeRequestOptions(ceremony.optionsJson());
    } catch (IllegalStateException exception) {
      throw new WebAuthnCeremonyInvalidException();
    }
    final PublicKeyCredential<AuthenticatorAssertionResponse> credential;
    if (!credentialNode.isObject()) {
      throw new WebAuthnVerificationFailedException();
    }
    try {
      credential = codec.decodeAssertionCredential(credentialNode);
    } catch (IllegalStateException exception) {
      throw new WebAuthnVerificationFailedException();
    }
    final PublicKeyCredentialUserEntity owner;
    try {
      owner =
          relyingPartyOperations.authenticate(
              new RelyingPartyAuthenticationRequest(options, credential));
    } catch (VerificationException | IllegalArgumentException exception) {
      throw new WebAuthnVerificationFailedException();
    }

    final var stored =
        credentials
            .findByCredentialId(credential.getRawId().toBase64UrlString())
            .filter(value -> value.isActive())
            .orElseThrow(WebAuthnVerificationFailedException::new);
    final var user =
        users
            .findWithPersonAndInstitutionById(stored.getUser().getId())
            .orElseThrow(WebAuthnVerificationFailedException::new);
    final byte[] handle = user.getWebauthnUserHandle();
    final var responseHandle = credential.getResponse().getUserHandle();
    // The library verifies the signature and resolves the stored owner; also bind the
    // discoverable assertion's userHandle explicitly instead of trusting client identity.
    if (owner == null
        || owner.getId() == null
        || handle == null
        || responseHandle == null
        || !Arrays.equals(owner.getId().getBytes(), handle)
        || !Arrays.equals(responseHandle.getBytes(), handle)
        || !credential.getId().equals(credential.getRawId().toBase64UrlString())
        || !user.getInstitutionId().equals(ceremony.institutionId())
        || !user.isAccountActive()) {
      throw new WebAuthnVerificationFailedException();
    }
    if (user.requiresEmailVerification()) {
      throw new PasskeyEmailVerificationRequiredException();
    }

    return sessionIssuer.issue(
        user,
        AuthRequestMetadata.clientIp(request),
        request.getHeader("User-Agent"),
        rememberMe,
        AuthenticationSessionIssuer.METHOD_PASSKEY);
  }

  private Set<String> trustedOrigins() {
    return hostContext
        .trustedRequestOrigin()
        .map(Set::of)
        .orElseGet(() -> Set.copyOf(properties.allowedOrigins()));
  }
}
