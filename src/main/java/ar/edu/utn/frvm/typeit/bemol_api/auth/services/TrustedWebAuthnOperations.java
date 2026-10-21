package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

@RequiredArgsConstructor
public final class TrustedWebAuthnOperations implements WebAuthnRelyingPartyOperations {
  private static final String LOCALHOST_RP_ID = "localhost";

  private final PublicKeyCredentialUserEntityRepository users;
  private final UserCredentialRepository credentials;
  private final PublicKeyCredentialRpEntity rp;
  private final WebAuthnProperties properties;
  private final InstitutionalHostContext context;

  public Set<String> trustedOrigins() {
    // A configured sibling origin must not override the current BFF host binding.
    return context
        .trustedRequestOrigin()
        .map(Set::of)
        .orElseGet(() -> Set.copyOf(properties.allowedOrigins()));
  }

  private Webauthn4JRelyingPartyOperations delegate() {
    final var operations =
        new Webauthn4JRelyingPartyOperations(
            users, credentials, relyingPartyForRequest(), trustedOrigins());
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

  private PublicKeyCredentialRpEntity relyingPartyForRequest() {
    if (!LOCALHOST_RP_ID.equalsIgnoreCase(properties.rpId())) {
      return rp;
    }

    // The deployment guard permits this configured RP ID only in dev/test. Browsers
    // treat localhost as a TLD, so local institutional hosts need their own RP ID.
    final var origin = context.trustedRequestOrigin();
    if (origin.isEmpty()) {
      return rp;
    }

    final var hostname = URI.create(origin.get()).getHost();
    if (hostname == null || !hostname.endsWith("." + LOCALHOST_RP_ID)) {
      return rp;
    }

    return PublicKeyCredentialRpEntity.builder().id(hostname).name(rp.getName()).build();
  }

  @Override
  public PublicKeyCredentialCreationOptions createPublicKeyCredentialCreationOptions(
      final PublicKeyCredentialCreationOptionsRequest request) {
    return delegate().createPublicKeyCredentialCreationOptions(request);
  }

  @Override
  public CredentialRecord registerCredential(final RelyingPartyRegistrationRequest request) {
    return delegate().registerCredential(request);
  }

  @Override
  public PublicKeyCredentialRequestOptions createCredentialRequestOptions(
      final PublicKeyCredentialRequestOptionsRequest request) {
    return delegate().createCredentialRequestOptions(request);
  }

  @Override
  public PublicKeyCredentialUserEntity authenticate(
      final RelyingPartyAuthenticationRequest request) {
    return delegate().authenticate(request);
  }
}
