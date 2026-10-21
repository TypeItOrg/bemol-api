package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsModule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RequestPasskeyAuthenticationOptionsUseCaseTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final Bytes userHandle = Bytes.random();
  private final Bytes knownCredentialId = Bytes.random();

  @Mock private LoginAttemptService loginAttemptService;
  @Mock private UserRepository userRepository;
  @Mock private WebAuthnCeremonyService ceremonyService;

  private final WebAuthnOptionsCodec codec =
      new WebAuthnOptionsCodec(
          JsonMapper.builder()
              .addModule(new WebauthnJacksonModule())
              .addModule(new WebAuthnOptionsModule())
              .build());

  private RequestPasskeyAuthenticationOptionsUseCase useCase;

  @BeforeEach
  void setUp() {
    final PublicKeyCredentialUserEntityRepository userEntities =
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
    final UserCredentialRepository credentials =
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
                    .credentialId(knownCredentialId)
                    .userEntityUserId(userHandle)
                    .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                    .signatureCount(0L)
                    .uvInitialized(false)
                    .transports(Set.of())
                    .backupEligible(false)
                    .backupState(false)
                    .label("key")
                    .created(Instant.now())
                    .build());
          }
        };
    final Webauthn4JRelyingPartyOperations operations =
        new Webauthn4JRelyingPartyOperations(
            userEntities,
            credentials,
            PublicKeyCredentialRpEntity.builder().id("localhost").name("Boero").build(),
            Set.of("http://localhost:3000"));
    operations.setCustomizeRequestOptions(
        builder -> builder.userVerification(UserVerificationRequirement.REQUIRED));
    useCase =
        new RequestPasskeyAuthenticationOptionsUseCase(
            loginAttemptService, userRepository, operations, ceremonyService, codec);
  }

  @Test
  @DisplayName("Should expose discoverable options and store a consistent ceremony")
  void execute_exposesDiscoverableOptionsAndStoresConsistentCeremony() {
    final User user = Mockito.mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(user.getInstitutionId()).thenReturn(INSTITUTION_ID);
    when(loginAttemptService.resolve("attempt"))
        .thenReturn(new LoginAttempt("attempt", USER_ID, INSTITUTION_ID, true, Instant.now()));
    when(userRepository.findWithPersonAndInstitutionById(USER_ID)).thenReturn(Optional.of(user));
    when(ceremonyService.storeAuthentication(any(), any(), any())).thenReturn("ceremony");

    final PasskeyAuthenticationOptionsResponse response = useCase.execute("attempt");

    final JsonNode wire = response.options();
    assertThat(wire.get("allowCredentials").isArray()).isTrue();
    assertThat(wire.get("allowCredentials")).isEmpty();
    assertThat(wire.get("challenge").isString()).isTrue();
    assertThat(wire.get("rpId").asString()).isEqualTo("localhost");
    assertThat(wire.get("userVerification").asString()).isEqualTo("required");

    final ArgumentCaptor<String> optionsJson = ArgumentCaptor.forClass(String.class);
    verify(ceremonyService).storeAuthentication(eq("attempt"), eq(USER_ID), optionsJson.capture());
    assertThat(wire).isEqualTo(codec.parseSnapshot(optionsJson.getValue()));
    final PublicKeyCredentialRequestOptions stored =
        codec.decodeRequestOptions(optionsJson.getValue());
    assertThat(stored.getAllowCredentials()).isEmpty();
    assertThat(stored.getChallenge().toBase64UrlString())
        .isEqualTo(wire.get("challenge").asString());
    assertThat(stored.getRpId()).isEqualTo("localhost");
  }

  @Test
  @DisplayName("Should reject passkey options for a password-only attempt")
  void execute_rejectsPasswordOnlyAttempt() {
    when(loginAttemptService.resolve("attempt"))
        .thenReturn(new LoginAttempt("attempt", USER_ID, INSTITUTION_ID, false, Instant.now()));

    assertThatThrownBy(() -> useCase.execute("attempt"))
        .isInstanceOf(InvalidLoginAttemptException.class);

    verifyNoInteractions(userRepository, ceremonyService);
  }

  private PublicKeyCredentialUserEntity userEntity() {
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .id(userHandle)
        .name(USER_ID.toString())
        .displayName("Display Name")
        .build();
  }
}
