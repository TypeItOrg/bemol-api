package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RecentAuthRequiredException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnCeremonyInvalidException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

@Testcontainers
@IntegrationTest
class RecentAuthCeremonyIntegrationTest {

  private static final String RECENT_KEY_PREFIX = "bemol:auth:recent:";

  @Container
  // The Testcontainers lifecycle owns and stops this shared container.
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private RecentAuthService recentAuthService;
  private WebAuthnCeremonyService ceremonyService;
  private WebAuthnProperties properties;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    properties =
        new WebAuthnProperties(
            "localhost",
            "Bemol",
            List.of("http://localhost:3000"),
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            10,
            Duration.ofMinutes(5));
    recentAuthService = new RecentAuthService(redisTemplate, properties);
    ceremonyService = new WebAuthnCeremonyService(redisTemplate, properties);
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("Should complete a ceremony started while recent-auth was valid")
  void verify_succeedsAfterRecentMarkerExpiresWhileCeremonyIsValid() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal = institutionalPrincipal(userId, institutionId, sessionId);
    final User user = user(userId, institutionId);
    recentAuthService.mark(sessionId, userId, AuthenticationSessionIssuer.METHOD_PASSWORD);
    final String ceremonyId = ceremonyService.storeRegistration(userId, sessionId, "Mi PC", "{}");
    expireRecentMarker(sessionId);

    final UserRepository userRepository = Mockito.mock(UserRepository.class);
    when(userRepository.findWithLockById(userId)).thenReturn(Optional.of(user));
    final PasskeyCredentialRepository passkeyCredentialRepository =
        Mockito.mock(PasskeyCredentialRepository.class);
    when(passkeyCredentialRepository.countActiveByUserId(any())).thenReturn(0L);
    when(passkeyCredentialRepository.findByCredentialId(any())).thenReturn(Optional.empty());
    final PasskeyCredential saved = Mockito.mock(PasskeyCredential.class);
    when(passkeyCredentialRepository.saveAndFlush(any())).thenReturn(saved);
    final PasskeyCredentialMapper mapper = Mockito.mock(PasskeyCredentialMapper.class);
    when(mapper.toEntity(any(), any(), any())).thenReturn(saved);
    final WebAuthnRelyingPartyOperations relyingPartyOperations =
        Mockito.mock(WebAuthnRelyingPartyOperations.class);
    when(relyingPartyOperations.registerCredential(any())).thenReturn(credentialRecord());
    final WebAuthnOptionsCodec codec = Mockito.mock(WebAuthnOptionsCodec.class);
    when(codec.decodeCreationOptions("{}")).thenReturn(creationOptions());
    when(codec.decodeAttestationCredential(any())).thenReturn(attestationCredential());
    final VerifyPasskeyRegistrationUseCase useCase =
        new VerifyPasskeyRegistrationUseCase(
            org.mockito.Mockito.mock(AuditEventRecorder.class),
            userRepository,
            passkeyCredentialRepository,
            mapper,
            relyingPartyOperations,
            ceremonyService,
            properties,
            codec);

    final PasskeyResponse response =
        useCase.execute(principal, ceremonyId, Mockito.mock(JsonNode.class));

    assertThat(recentAuthService.isRecent(sessionId, userId)).isFalse();
    assertThat(response).isNotNull();
    verify(passkeyCredentialRepository).saveAndFlush(any());
  }

  @Test
  @DisplayName("Should reject new registration options after recent-auth expires")
  void registrationOptions_rejectedAfterRecentMarkerExpires() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal = institutionalPrincipal(userId, institutionId, sessionId);
    recentAuthService.mark(sessionId, userId, AuthenticationSessionIssuer.METHOD_PASSWORD);
    expireRecentMarker(sessionId);

    final UserRepository userRepository = Mockito.mock(UserRepository.class);
    final PasskeyCredentialRepository passkeyCredentialRepository =
        Mockito.mock(PasskeyCredentialRepository.class);
    final WebAuthnUserHandleService userHandleService =
        Mockito.mock(WebAuthnUserHandleService.class);
    final WebAuthnRelyingPartyOperations relyingPartyOperations =
        Mockito.mock(WebAuthnRelyingPartyOperations.class);
    final WebAuthnOptionsCodec codec = Mockito.mock(WebAuthnOptionsCodec.class);
    final RequestPasskeyRegistrationOptionsUseCase useCase =
        new RequestPasskeyRegistrationOptionsUseCase(
            userRepository,
            passkeyCredentialRepository,
            userHandleService,
            relyingPartyOperations,
            ceremonyService,
            recentAuthService,
            properties,
            codec);

    assertThatThrownBy(() -> useCase.execute(principal, "Mi PC"))
        .isInstanceOf(RecentAuthRequiredException.class);
  }

  @Test
  @DisplayName("Should reject a ceremony bound to another session")
  void verify_rejectsCeremonyBoundToAnotherSession() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final UUID callerSessionId = UUID.randomUUID();
    final UUID otherSessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, institutionId, callerSessionId);
    final String ceremonyId =
        ceremonyService.storeRegistration(userId, otherSessionId, "Mi PC", "{}");

    final VerifyPasskeyRegistrationUseCase useCase =
        new VerifyPasskeyRegistrationUseCase(
            org.mockito.Mockito.mock(AuditEventRecorder.class),
            Mockito.mock(UserRepository.class),
            Mockito.mock(PasskeyCredentialRepository.class),
            Mockito.mock(PasskeyCredentialMapper.class),
            Mockito.mock(WebAuthnRelyingPartyOperations.class),
            ceremonyService,
            properties,
            Mockito.mock(WebAuthnOptionsCodec.class));

    assertThatThrownBy(() -> useCase.execute(principal, ceremonyId, Mockito.mock(JsonNode.class)))
        .isInstanceOf(WebAuthnCeremonyInvalidException.class);
  }

  @Test
  @DisplayName("Should require current recent-auth to revoke a passkey")
  void revoke_requiresCurrentRecentAuth() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal = institutionalPrincipal(userId, institutionId, sessionId);
    recentAuthService.mark(sessionId, userId, AuthenticationSessionIssuer.METHOD_PASSWORD);
    expireRecentMarker(sessionId);

    final PasskeyCredentialRepository passkeyCredentialRepository =
        Mockito.mock(PasskeyCredentialRepository.class);
    final RevokePasskeyUseCase useCase =
        new RevokePasskeyUseCase(
            org.mockito.Mockito.mock(AuditEventRecorder.class),
            Clock.systemUTC(),
            passkeyCredentialRepository,
            recentAuthService);

    assertThatThrownBy(() -> useCase.execute(principal, UUID.randomUUID()))
        .isInstanceOf(RecentAuthRequiredException.class);
    verify(passkeyCredentialRepository, never()).findWithLockByIdAndUserId(any(), any());
  }

  private void expireRecentMarker(final UUID sessionId) {
    redisTemplate.delete(RECENT_KEY_PREFIX + sessionId);
  }

  private static User user(final UUID userId, final UUID institutionId) {
    final Institution institution = Institution.builder().id(institutionId).build();
    return User.builder()
        .id(userId)
        .institution(institution)
        .person(
            Person.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .documentNumber("12345678")
                .build())
        .password("hash")
        .build();
  }

  private static CredentialRecord credentialRecord() {
    return ImmutableCredentialRecord.builder()
        .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
        .credentialId(Bytes.random())
        .userEntityUserId(Bytes.random())
        .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
        .signatureCount(0L)
        .uvInitialized(false)
        .transports(Set.of(AuthenticatorTransport.INTERNAL))
        .backupEligible(false)
        .backupState(false)
        .label("Mi PC")
        .created(Instant.now())
        .build();
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
        .pubKeyCredParams(List.of(PublicKeyCredentialParameters.ES256))
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

  @SuppressWarnings("unchecked")
  private static PublicKeyCredential<AuthenticatorAttestationResponse> attestationCredential() {
    return Mockito.mock(PublicKeyCredential.class);
  }
}
