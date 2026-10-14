package ar.edu.utn.frvm.typeit.boero_api.auth.webauthn;

import java.util.Locale;
import java.util.Map;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;

final class WebAuthnOptionValues {
  private WebAuthnOptionValues() {}

  private static final Map<Long, PublicKeyCredentialParameters> CREDENTIAL_ALGORITHMS =
      Map.of(
          -7L, PublicKeyCredentialParameters.ES256,
          -35L, PublicKeyCredentialParameters.ES384,
          -36L, PublicKeyCredentialParameters.ES512,
          -257L, PublicKeyCredentialParameters.RS256,
          -258L, PublicKeyCredentialParameters.RS384,
          -259L, PublicKeyCredentialParameters.RS512,
          -8L, PublicKeyCredentialParameters.EdDSA,
          -65535L, PublicKeyCredentialParameters.RS1);

  static PublicKeyCredentialParameters credentialAlgorithm(final long algorithm) {
    final var parameter = CREDENTIAL_ALGORITHMS.get(algorithm);
    if (parameter == null) {
      throw new IllegalArgumentException("Unsupported WebAuthn credential algorithm");
    }
    return parameter;
  }

  static AuthenticatorTransport transport(final String value) {
    final var normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "USB" -> AuthenticatorTransport.USB;
      case "NFC" -> AuthenticatorTransport.NFC;
      case "BLE" -> AuthenticatorTransport.BLE;
      case "SMART_CARD" -> AuthenticatorTransport.SMART_CARD;
      case "HYBRID" -> AuthenticatorTransport.HYBRID;
      case "INTERNAL" -> AuthenticatorTransport.INTERNAL;
      default -> throw new IllegalArgumentException("Unsupported WebAuthn transport");
    };
  }

  static ResidentKeyRequirement residentKey(final String value) {
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "required" -> ResidentKeyRequirement.REQUIRED;
      case "preferred" -> ResidentKeyRequirement.PREFERRED;
      case "discouraged" -> ResidentKeyRequirement.DISCOURAGED;
      default -> throw new IllegalArgumentException("Unsupported WebAuthn resident key");
    };
  }

  static UserVerificationRequirement userVerification(final String value) {
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "required" -> UserVerificationRequirement.REQUIRED;
      case "preferred" -> UserVerificationRequirement.PREFERRED;
      case "discouraged" -> UserVerificationRequirement.DISCOURAGED;
      default -> throw new IllegalArgumentException("Unsupported WebAuthn user verification");
    };
  }

  static AuthenticatorAttachment attachment(final String value) {
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "platform" -> AuthenticatorAttachment.PLATFORM;
      case "cross-platform" -> AuthenticatorAttachment.CROSS_PLATFORM;
      default ->
          throw new IllegalArgumentException("Unsupported WebAuthn authenticator attachment");
    };
  }

  static AttestationConveyancePreference attestation(final String value) {
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "none" -> AttestationConveyancePreference.NONE;
      case "indirect" -> AttestationConveyancePreference.INDIRECT;
      case "direct" -> AttestationConveyancePreference.DIRECT;
      case "enterprise" -> AttestationConveyancePreference.ENTERPRISE;
      default -> throw new IllegalArgumentException("Unsupported WebAuthn attestation");
    };
  }
}
