package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PasswordRecoveryProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.AccountEmailChangeToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasswordRecoveryRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  JpaAuditingTestConfig.class,
  UpdatePersonUseCase.class,
  AccountEmailChangeUseCase.class,
  RequestInstitutionalPasswordRecoveryUseCase.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@IntegrationTest
class ProfileEmailConcurrencyPostgresIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
  private static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:18-alpine", "profile_email_concurrency");

  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private JdbcTemplate database;
  @Autowired private PersonRepository people;
  @Autowired private UpdatePersonUseCase updateProfile;
  @Autowired private AccountEmailChangeUseCase emailChanges;
  @Autowired private RequestInstitutionalPasswordRecoveryUseCase recovery;

  @MockitoBean private AuditEventRecorder audit;
  @MockitoBean private PasswordEncoder encoder;
  @MockitoBean private AccountAccessAuthorization access;
  @MockitoBean private AccountActionRateLimiter limiter;
  @MockitoBean private InstitutionalAccountManagementUseCase accounts;
  @MockitoBean private OperatingContextResolver operatingContext;
  @MockitoBean private SessionRevocationService sessions;
  @MockitoBean private InstitutionalHostContext hostContext;
  @MockitoBean private PasswordRecoveryProperties recoveryProperties;
  @MockitoBean private Clock clock;

  @DynamicPropertySource
  static void databaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.sql-migration-prefix", () -> "");
  }

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(clock.withZone(any()))
        .thenAnswer(invocation -> Clock.fixed(NOW, invocation.getArgument(0)));
    when(access.canManageInstitutionally(any(), any())).thenReturn(true);
    when(recoveryProperties.tokenExpiration()).thenReturn(Duration.ofMinutes(30));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void staleOrdinaryProfileUpdatePreservesTheProvenRecoveryEmailAndNewToken(
      final boolean submitsUnchangedEmail) throws Exception {
    final Fixture fixture = fixture();
    final var loaded = new CountDownLatch(1);
    final var resume = new CountDownLatch(1);
    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var ordinaryUpdate =
          executor.submit(
              () ->
                  tx(
                      () -> {
                        final Person snapshot =
                            people
                                .findByIdAndInstitution_Id(
                                    fixture.principal().personId(),
                                    fixture.principal().institutionId())
                                .orElseThrow();
                        final String email = submitsUnchangedEmail ? snapshot.getEmail() : null;
                        loaded.countDown();
                        await(resume);
                        return updateProfile.execute(
                            fixture.principal(),
                            new UpdatePersonRequest(
                                null, null, null, email, "0353-123456", null, null, null));
                      }));
      try {
        assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
        final String replacementToken =
            tx(
                () -> {
                  emailChanges.confirm(fixture.emailToken());
                  recovery.execute(
                      new PasswordRecoveryRequest(
                          fixture.document(), fixture.principal().institutionId()));
                  entityManager.flush();
                  return tokenHash(fixture.principal().userId());
                });
        assertThat(replacementToken).isNotEqualTo("old-reset-hash");
        resume.countDown();
        ordinaryUpdate.get(10, TimeUnit.SECONDS);

        tx(
            () -> {
              final Person current =
                  entityManager.find(Person.class, fixture.principal().personId());
              assertThat(current.getEmail()).isEqualTo("replacement@example.com");
              assertThat(current.getPhoneNumber()).isEqualTo("0353-123456");
              assertThat(tokenHash(fixture.principal().userId())).isEqualTo(replacementToken);
              return null;
            });
      } finally {
        resume.countDown();
      }
    }
    verify(sessions).revokeInstitutionalSessionsForUser(fixture.principal().userId());
  }

  private Fixture fixture() {
    return tx(
        () -> {
          final var institution =
              createInstitution(entityManager, "profile-email-" + UUID.randomUUID());
          final var user = createUser(entityManager, institution, "12345678");
          final String emailToken = "verified-email-" + UUID.randomUUID();
          entityManager.persist(
              AccountEmailChangeToken.issue(
                  user,
                  user.getPerson(),
                  null,
                  "replacement@example.com",
                  RequestInstitutionalPasswordRecoveryUseCase.hash(emailToken),
                  NOW,
                  NOW.plusSeconds(600)));
          entityManager.persist(
              InstitutionalPasswordResetToken.builder()
                  .user(user)
                  .tokenHash("old-reset-hash")
                  .expiresAt(NOW.plusSeconds(600))
                  .build());
          entityManager.flush();
          return new Fixture(
              JwtAuthenticatedUser.builder()
                  .userId(user.getId())
                  .personId(user.getPerson().getId())
                  .institutionId(institution.getId())
                  .sessionId(UUID.fromString("00000000-0000-0000-0000-000000000006"))
                  .build(),
              user.getDocumentNumber(),
              emailToken);
        });
  }

  private String tokenHash(final UUID userId) {
    final String tokenHash =
        database.queryForObject(
            "select token_hash from institutional_password_reset_tokens where user_id = ?",
            String.class,
            userId);
    assertThat(tokenHash).isNotNull();
    return tokenHash;
  }

  private <T> T tx(final Supplier<T> action) {
    final var transaction = new TransactionTemplate(transactions);
    transaction.setTimeout(15);
    return transaction.execute(status -> action.get());
  }

  private static void await(final CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the concurrent email change");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private record Fixture(JwtAuthenticatedUser principal, String document, String emailToken) {}
}
