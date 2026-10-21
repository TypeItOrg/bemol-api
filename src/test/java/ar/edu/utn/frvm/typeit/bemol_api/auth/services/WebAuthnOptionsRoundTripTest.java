package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsModule;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import tools.jackson.databind.json.JsonMapper;

class WebAuthnOptionsRoundTripTest {

  private final WebAuthnOptionsCodec codec =
      new WebAuthnOptionsCodec(
          JsonMapper.builder()
              .addModule(new WebauthnJacksonModule())
              .addModule(new WebAuthnOptionsModule())
              .build());

  @Test
  @DisplayName("Should roundtrip registration options through JSON")
  void creationOptions_roundTrip() {
    final PublicKeyCredentialCreationOptions options = creationOptions();

    final PublicKeyCredentialCreationOptions restored =
        codec.decodeCreationOptions(codec.encodeCreationOptions(options));

    assertThat(restored.getChallenge()).isEqualTo(options.getChallenge());
    assertThat(restored.getRp().getId()).isEqualTo("localhost");
    assertThat(restored.getRp().getName()).isEqualTo("Bemol");
    assertThat(restored.getUser().getId()).isEqualTo(options.getUser().getId());
    assertThat(restored.getUser().getName()).isEqualTo("user-id");
    assertThat(restored.getUser().getDisplayName()).isEqualTo("Display Name");
    assertThat(restored.getAttestation()).isEqualTo(AttestationConveyancePreference.NONE);
    assertThat(restored.getTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(restored.getAuthenticatorSelection().getResidentKey())
        .isEqualTo(ResidentKeyRequirement.REQUIRED);
    assertThat(restored.getAuthenticatorSelection().getUserVerification())
        .isEqualTo(UserVerificationRequirement.REQUIRED);
    assertThat(restored.getPubKeyCredParams())
        .extracting(param -> param.getAlg().getValue())
        .containsExactly(-7L, -257L);
    assertThat(restored.getExcludeCredentials()).isEmpty();
  }

  @Test
  @DisplayName("Should roundtrip authentication request options through JSON")
  void requestOptions_roundTrip() {
    final PublicKeyCredentialRequestOptions options = requestOptions();

    final PublicKeyCredentialRequestOptions restored =
        codec.decodeRequestOptions(codec.encodeRequestOptions(options));

    assertThat(restored.getChallenge()).isEqualTo(options.getChallenge());
    assertThat(restored.getRpId()).isEqualTo("localhost");
    assertThat(restored.getTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(restored.getUserVerification()).isEqualTo(UserVerificationRequirement.REQUIRED);
    assertThat(restored.getAllowCredentials()).isEmpty();
  }

  @Test
  @DisplayName("Should fail closed on unknown attestation values")
  void creationOptions_rejectsUnknownAttestation() {
    final String tampered =
        codec.encodeCreationOptions(creationOptions()).replace("\"none\"", "\"bogus\"");

    assertThatThrownBy(() -> codec.decodeCreationOptions(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should fail closed on unknown credential algorithms")
  void creationOptions_rejectsUnknownAlgorithm() {
    final String tampered =
        codec.encodeCreationOptions(creationOptions()).replace("\"alg\":-7", "\"alg\":-999");

    assertThatThrownBy(() -> codec.decodeCreationOptions(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should fail closed on non-empty extensions")
  void requestOptions_rejectsNonEmptyExtensions() {
    final String tampered =
        codec
            .encodeRequestOptions(requestOptions())
            .replace("\"extensions\":{}", "\"extensions\":{\"appid\":\"x\"}");

    assertThatThrownBy(() -> codec.decodeRequestOptions(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should fail closed on malformed payloads")
  void decode_rejectsMalformedPayloads() {
    assertThatThrownBy(() -> codec.decodeCreationOptions("not-json"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> codec.decodeRequestOptions("{\"challenge\":123}"))
        .isInstanceOf(IllegalStateException.class);
  }

  private static PublicKeyCredentialCreationOptions creationOptions() {
    return PublicKeyCredentialCreationOptions.builder()
        .rp(PublicKeyCredentialRpEntity.builder().id("localhost").name("Bemol").build())
        .user(
            ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name("user-id")
                .displayName("Display Name")
                .build())
        .challenge(Bytes.random())
        .pubKeyCredParams(
            List.of(PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.RS256))
        .timeout(Duration.ofMinutes(5))
        .excludeCredentials(List.of())
        .authenticatorSelection(
            AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build())
        .attestation(AttestationConveyancePreference.NONE)
        .build();
  }

  private static PublicKeyCredentialRequestOptions requestOptions() {
    return PublicKeyCredentialRequestOptions.builder()
        .challenge(Bytes.random())
        .timeout(Duration.ofMinutes(5))
        .rpId("localhost")
        .allowCredentials(List.of())
        .userVerification(UserVerificationRequirement.REQUIRED)
        .build();
  }
}
