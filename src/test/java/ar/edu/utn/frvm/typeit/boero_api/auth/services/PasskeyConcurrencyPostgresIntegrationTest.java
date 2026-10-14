package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createUser;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.DuplicatePasskeyCredentialException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.PasskeyNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.node.JsonNodeFactory;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  JpaAuditingTestConfig.class,
  PasskeyCredentialMapper.class,
  PasskeyUserCredentialRepository.class,
  RevokePasskeyUseCase.class,
  RenamePasskeyUseCase.class,
  VerifyPasskeyRegistrationUseCase.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@IntegrationTest
class PasskeyConcurrencyPostgresIntegrationTest {
  static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:17-alpine", "passkey_concurrency");

  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private PasskeyCredentialRepository credentials;
  @Autowired private PasskeyUserCredentialRepository adapter;
  @Autowired private RevokePasskeyUseCase revoke;
  @Autowired private RenamePasskeyUseCase rename;
  @MockitoBean private RecentAuthService recentAuth;
  @Autowired private VerifyPasskeyRegistrationUseCase registration;
  @MockitoBean private WebAuthnCeremonyService ceremonies;
  @MockitoBean private WebAuthnProperties webAuthnProperties;
  @MockitoBean private WebAuthnOptionsCodec codec;
  @MockitoBean private WebAuthnRelyingPartyOperations relyingParty;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.sql-migration-prefix", () -> "");
  }

  @Test
  @DisplayName("Should preserve revocation when authentication already holds the credential lock")
  void authenticationCannotOverwriteConcurrentRevocation() throws Exception {
    final Fixture fixture = fixture();
    final CountDownLatch loaded = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final CountDownLatch revoking = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var authentication =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            final var record = adapter.findByCredentialId(fixture.externalId());
                            assertThat(record).isNotNull();
                            loaded.countDown();
                            await(release);
                            adapter.save(
                                ImmutableCredentialRecord.fromCredentialRecord(record)
                                    .signatureCount(1L)
                                    .build());
                          }));
      try {
        assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
        final var revocation =
            executor.submit(
                () -> {
                  revoking.countDown();
                  revoke.execute(fixture.principal(), fixture.id());
                });
        assertThat(revoking.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> revocation.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        release.countDown();
        authentication.get(10, TimeUnit.SECONDS);
        revocation.get(10, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              final PasskeyCredential stored = credentials.findById(fixture.id()).orElseThrow();
              assertThat(stored.isActive()).isFalse();
              assertThat(stored.getSignatureCount()).isEqualTo(1L);
              assertThat(adapter.findByCredentialId(fixture.externalId())).isNull();
            });
  }

  @Test
  @DisplayName("Should reject use and rename after revocation commits")
  void revokedCredentialCannotBeUsedOrRenamed() {
    final Fixture fixture = fixture();
    revoke.execute(fixture.principal(), fixture.id());
    assertThat(adapter.findByCredentialId(fixture.externalId())).isNull();
    assertThatThrownBy(() -> rename.execute(fixture.principal(), fixture.id(), "Other"))
        .isInstanceOf(PasskeyNotFoundException.class);
  }

  @Test
  @DisplayName("Should translate the actual Flyway credential constraint to a duplicate error")
  void translatesRealCredentialConstraint() {
    final Fixture fixture = fixture();
    when(webAuthnProperties.maxPasskeys()).thenReturn(10);
    when(ceremonies.consumeRegistration("ceremony"))
        .thenReturn(
            Optional.of(
                new RegistrationCeremony(
                    "ceremony",
                    fixture.principal().userId(),
                    fixture.principal().sessionId(),
                    "Duplicate",
                    "{}",
                    Instant.now())));
    when(codec.decodeCreationOptions("{}"))
        .thenReturn(Mockito.mock(PublicKeyCredentialCreationOptions.class));
    @SuppressWarnings("unchecked")
    final PublicKeyCredential<AuthenticatorAttestationResponse> credential =
        Mockito.mock(PublicKeyCredential.class);
    when(codec.decodeAttestationCredential(any())).thenReturn(credential);
    final byte[] handle =
        new TransactionTemplate(transactionManager)
            .execute(
                status ->
                    credentials
                        .findById(fixture.id())
                        .orElseThrow()
                        .getUser()
                        .getWebauthnUserHandle());
    when(relyingParty.registerCredential(any()))
        .thenReturn(
            ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(fixture.externalId())
                .userEntityUserId(new Bytes(requireNonNull(handle)))
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                .signatureCount(0)
                .transports(Set.of())
                .label("Duplicate")
                .created(Instant.now())
                .build());
    doReturn(Optional.empty())
        .when(credentials)
        .findByCredentialId(fixture.externalId().toBase64UrlString());
    assertThatThrownBy(
            () ->
                registration.execute(
                    fixture.principal(), "ceremony", JsonNodeFactory.instance.objectNode()))
        .isInstanceOf(DuplicatePasskeyCredentialException.class);
  }

  private Fixture fixture() {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              final var institution =
                  createInstitution(entityManager, UUID.randomUUID().toString());
              final User user = createUser(entityManager, institution, "12345678");
              user.ensureWebAuthnUserHandle(new SecureRandom());
              final Bytes externalId = Bytes.random();
              final PasskeyCredential credential =
                  credentials.saveAndFlush(
                      PasskeyCredential.builder()
                          .user(user)
                          .credentialId(externalId.toBase64UrlString())
                          .publicKeyCose(new byte[] {1, 2, 3})
                          .userHandle(requireNonNull(user.getWebauthnUserHandle()))
                          .label("Test key")
                          .build());
              return new Fixture(
                  credential.getId(),
                  externalId,
                  JwtAuthenticatedUser.builder()
                      .userId(user.getId())
                      .institutionId(institution.getId())
                      .sessionId(UUID.randomUUID())
                      .build());
            });
  }

  private static void await(final CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("Timed out waiting for transaction");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private record Fixture(UUID id, Bytes externalId, JwtAuthenticatedUser principal) {}
}
