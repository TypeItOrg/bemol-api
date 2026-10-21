package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsModule;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WebAuthnSpringGeneratedRoundTripTest {

  private final Bytes userHandle = Bytes.random();
  private final Bytes credentialId = Bytes.random();

  private final WebAuthnOptionsCodec codec =
      new WebAuthnOptionsCodec(
          JsonMapper.builder()
              .addModule(new WebauthnJacksonModule())
              .addModule(new WebAuthnOptionsModule())
              .build());

  private final PublicKeyCredentialUserEntityRepository userEntities =
      new PublicKeyCredentialUserEntityRepository() {
        @Override
        public PublicKeyCredentialUserEntity findById(final Bytes id) {
          return userEntity();
        }

        @Override
        public PublicKeyCredentialUserEntity findByUsername(final String username) {
          return userEntity();
        }

        @Override
        public void save(final PublicKeyCredentialUserEntity userEntity) {}

        @Override
        public void delete(final Bytes id) {}
      };

  private final UserCredentialRepository credentials =
      new UserCredentialRepository() {
        @Override
        public void delete(final Bytes credentialId) {}

        @Override
        public void save(final CredentialRecord credentialRecord) {}

        @Override
        public @Nullable CredentialRecord findByCredentialId(final Bytes credentialId) {
          return null;
        }

        @Override
        public List<CredentialRecord> findByUserId(final Bytes userId) {
          return List.of(
              ImmutableCredentialRecord.builder()
                  .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                  .credentialId(credentialId)
                  .userEntityUserId(userHandle)
                  .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                  .signatureCount(0L)
                  .uvInitialized(false)
                  .transports(
                      Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID))
                  .backupEligible(false)
                  .backupState(false)
                  .label("key")
                  .created(Instant.now())
                  .build());
        }
      };

  private final WebAuthnRelyingPartyOperations operations =
      new Webauthn4JRelyingPartyOperations(
          userEntities,
          credentials,
          PublicKeyCredentialRpEntity.builder().id("localhost").name("Bemol").build(),
          Set.of("http://localhost:3000"));

  @Test
  @DisplayName("Should preserve Spring registration options through snapshot round-trip")
  void springCreationOptions_roundTrip() {
    final Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated("user-id", null, List.of());
    final PublicKeyCredentialCreationOptions generated =
        operations.createPublicKeyCredentialCreationOptions(
            new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication));

    final PublicKeyCredentialCreationOptions restored =
        codec.decodeCreationOptions(codec.encodeCreationOptions(generated));

    assertThat(restored.getChallenge()).isEqualTo(generated.getChallenge());
    assertThat(restored.getRp().getId()).isEqualTo(generated.getRp().getId());
    assertThat(restored.getRp().getName()).isEqualTo(generated.getRp().getName());
    assertThat(restored.getUser().getId()).isEqualTo(generated.getUser().getId());
    assertThat(restored.getUser().getName()).isEqualTo(generated.getUser().getName());
    assertThat(restored.getUser().getDisplayName()).isEqualTo(generated.getUser().getDisplayName());
    assertThat(restored.getAttestation()).isEqualTo(generated.getAttestation());
    assertThat(restored.getTimeout()).isEqualTo(generated.getTimeout());
    assertThat(restored.getAuthenticatorSelection().getResidentKey())
        .isEqualTo(generated.getAuthenticatorSelection().getResidentKey());
    assertThat(restored.getAuthenticatorSelection().getUserVerification())
        .isEqualTo(generated.getAuthenticatorSelection().getUserVerification());
    assertThat(restored.getPubKeyCredParams())
        .extracting(param -> param.getAlg().getValue())
        .containsExactlyElementsOf(
            generated.getPubKeyCredParams().stream()
                .map(param -> param.getAlg().getValue())
                .toList());
    assertThat(restored.getExcludeCredentials())
        .extracting(descriptor -> descriptor.getId())
        .containsExactlyElementsOf(
            generated.getExcludeCredentials().stream()
                .map(descriptor -> descriptor.getId())
                .toList());
    assertThat(restored.getExcludeCredentials())
        .extracting(
            descriptor ->
                requireNonNull(descriptor.getTransports()).stream()
                    .map(mappedAuthenticatorTransport -> mappedAuthenticatorTransport.getValue())
                    .sorted()
                    .toList())
        .containsExactlyElementsOf(
            generated.getExcludeCredentials().stream()
                .map(
                    descriptor ->
                        requireNonNull(descriptor.getTransports()).stream()
                            .map(
                                mappedAuthenticatorTransport ->
                                    mappedAuthenticatorTransport.getValue())
                            .sorted()
                            .toList())
                .toList());
    assertThat(extensionInputs(restored)).isEqualTo(extensionInputs(generated));
  }

  @Test
  @DisplayName("Should preserve Spring authentication options through snapshot round-trip")
  void springRequestOptions_roundTrip() {
    final Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated("user-id", null, List.of());
    final PublicKeyCredentialRequestOptions generated =
        operations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(authentication));

    final PublicKeyCredentialRequestOptions restored =
        codec.decodeRequestOptions(codec.encodeRequestOptions(generated));

    assertThat(restored.getChallenge()).isEqualTo(generated.getChallenge());
    assertThat(restored.getRpId()).isEqualTo(generated.getRpId());
    assertThat(restored.getTimeout()).isEqualTo(generated.getTimeout());
    assertThat(restored.getUserVerification()).isEqualTo(generated.getUserVerification());
    assertThat(restored.getAllowCredentials())
        .extracting(descriptor -> descriptor.getId())
        .containsExactlyElementsOf(
            generated.getAllowCredentials().stream()
                .map(descriptor -> descriptor.getId())
                .toList());
    assertThat(extensionInputs(restored)).isEqualTo(extensionInputs(generated));
  }

  @Test
  @DisplayName("Should issue discoverable authentication options without credential descriptors")
  void unauthenticatedRequest_yieldsEmptyAllowCredentials() {
    final Authentication anonymous =
        UsernamePasswordAuthenticationToken.unauthenticated("user-id", null);
    final PublicKeyCredentialRequestOptions generated =
        operations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(anonymous));

    assertThat(generated.getAllowCredentials()).isEmpty();
    assertThat(generated.getChallenge()).isNotNull();
    assertThat(generated.getRpId()).isEqualTo("localhost");
    assertThat(generated.getTimeout()).isNotNull();
    assertThat(generated.getUserVerification()).isEqualTo(UserVerificationRequirement.PREFERRED);
    assertThat(
            codec
                .parseSnapshot(codec.encodeRequestOptions(generated))
                .get("allowCredentials")
                .isEmpty())
        .isTrue();
  }

  @Test
  @DisplayName("Should expose wire options as an explicit plain-JSON contract")
  void wireTree_exposesExplicitContract() {
    final Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated("user-id", null, List.of());
    final PublicKeyCredentialCreationOptions creation =
        operations.createPublicKeyCredentialCreationOptions(
            new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication));
    final PublicKeyCredentialRequestOptions request =
        operations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(authentication));

    final JsonNode creationTree = codec.parseSnapshot(codec.encodeCreationOptions(creation));
    final JsonNode requestTree = codec.parseSnapshot(codec.encodeRequestOptions(request));

    assertThat(requestTree.propertyNames())
        .containsExactlyInAnyOrder(
            "challenge", "rpId", "timeout", "allowCredentials", "userVerification", "extensions");
    assertThat(requestTree.get("challenge").asString())
        .isEqualTo(request.getChallenge().toBase64UrlString());
    assertThat(requestTree.get("rpId").asString()).isEqualTo("localhost");
    assertThat(requestTree.get("timeout").asLong()).isEqualTo(request.getTimeout().toMillis());
    assertThat(requestTree.get("userVerification").asString())
        .isEqualTo(requireNonNull(request.getUserVerification()).getValue());
    assertThat(requestTree.get("allowCredentials")).hasSize(1);
    assertThat(requestTree.get("allowCredentials").get(0).get("id").asString())
        .isEqualTo(credentialId.toBase64UrlString());
    assertThat(requestTree.get("allowCredentials").get(0).get("type").asString())
        .isEqualTo("public-key");

    assertThat(creationTree.propertyNames())
        .containsExactlyInAnyOrder(
            "challenge",
            "rp",
            "user",
            "pubKeyCredParams",
            "timeout",
            "excludeCredentials",
            "authenticatorSelection",
            "attestation",
            "extensions");
    assertThat(creationTree.get("challenge").asString())
        .isEqualTo(creation.getChallenge().toBase64UrlString());
    assertThat(creationTree.get("rp").get("id").asString()).isEqualTo("localhost");
    assertThat(creationTree.get("rp").get("name").asString()).isEqualTo("Bemol");
    assertThat(creationTree.get("user").get("id").asString())
        .isEqualTo(userHandle.toBase64UrlString());
    assertThat(creationTree.get("user").get("name").asString()).isEqualTo("user-id");
    assertThat(creationTree.get("attestation").asString())
        .isEqualTo(requireNonNull(creation.getAttestation()).getValue());
    assertThat(creationTree.get("authenticatorSelection").get("residentKey").asString())
        .isEqualTo(
            requireNonNull(creation.getAuthenticatorSelection().getResidentKey()).getValue());
    assertThat(creationTree.get("excludeCredentials")).hasSize(1);
    assertThat(creationTree.get("extensions").get("credProps").asBoolean()).isTrue();
  }

  private PublicKeyCredentialUserEntity userEntity() {
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .id(userHandle)
        .name("user-id")
        .displayName("Display Name")
        .build();
  }

  private static List<String> extensionInputs(final PublicKeyCredentialCreationOptions options) {
    if (options.getExtensions() == null) {
      return List.of();
    }
    return options.getExtensions().getInputs().stream()
        .map(input -> input.getExtensionId() + "=" + input.getInput())
        .sorted()
        .toList();
  }

  private static List<String> extensionInputs(final PublicKeyCredentialRequestOptions options) {
    if (options.getExtensions() == null) {
      return List.of();
    }
    return options.getExtensions().getInputs().stream()
        .map(input -> input.getExtensionId() + "=" + input.getInput())
        .sorted()
        .toList();
  }
}
