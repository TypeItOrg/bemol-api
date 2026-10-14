package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createUser;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.person;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.EmailVerificationProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.InstitutionalEmailVerificationToken;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidPasswordRecoveryTokenException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.InstitutionalEmailVerificationTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ChangePendingEmailRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ConfirmEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ResendEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ResetPasswordRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
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
  InstitutionalEmailVerificationUseCase.class,
  ResetInstitutionalPasswordUseCase.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@IntegrationTest
class EmailVerificationPostgresIntegrationTest {
  static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:17-alpine", "email_verification");

  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager transactions;
  @Autowired InstitutionalEmailVerificationUseCase verification;
  @Autowired ResetInstitutionalPasswordUseCase resetPassword;
  @Autowired InstitutionalEmailVerificationTokenRepository tokens;
  @Autowired InstitutionalPasswordResetTokenRepository resetTokens;

  @MockitoBean
  private ar.edu.utn.frvm.typeit.boero_api.auth.services.InstitutionalHostContext hostContext;

  @MockitoBean Clock clock;
  @MockitoBean EmailVerificationProperties properties;
  @MockitoBean PasswordEncoder encoder;
  @MockitoBean SessionRevocationService sessions;
  final Instant now = Instant.parse("2026-09-12T00:00:00Z");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
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
    when(clock.instant()).thenReturn(now);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(properties.tokenExpiration()).thenReturn(Duration.ofHours(24));
    when(properties.resendInterval()).thenReturn(Duration.ofMinutes(1));
    when(encoder.matches("correct-password", "encoded-password")).thenReturn(true);
  }

  <T> T tx(Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  record Fixture(UUID userId, UUID institutionId, String document, String raw) {}

  Fixture fixture() {
    return tx(
        () -> {
          final var institution = createInstitution(em, "verify-" + UUID.randomUUID());
          final Person person = person(institution, "76543210");
          em.persist(person);
          final User user =
              User.builder()
                  .institution(institution)
                  .person(person)
                  .password("encoded-password")
                  .emailVerificationStatus(EmailVerificationStatus.PENDING)
                  .build();
          em.persist(user);
          em.flush();
          final String raw = UUID.randomUUID().toString();
          em.persist(
              InstitutionalEmailVerificationToken.issue(
                  user,
                  RequestInstitutionalPasswordRecoveryUseCase.hash(raw),
                  now.minusSeconds(120),
                  Duration.ofHours(24)));
          return new Fixture(user.getId(), institution.getId(), person.getDocumentNumber(), raw);
        });
  }

  @Test
  void migrationPreservesExistingAccountsAndEnforcesUniqueTokenPerUser() {
    final Fixture f = fixture();
    final UUID existing =
        tx(
            () ->
                createUser(em, em.find(User.class, f.userId()).getInstitution(), "12345679")
                    .getId());
    assertThat(tx(() -> em.find(User.class, existing).getEmailVerificationStatus()))
        .isEqualTo(EmailVerificationStatus.NOT_REQUIRED);
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      em.persist(
                          InstitutionalEmailVerificationToken.issue(
                              em.find(User.class, f.userId()), "other", now, Duration.ofHours(24)));
                      em.flush();
                      return null;
                    }))
        .hasStackTraceContaining("email_verification_user_unique");
    assertThat(tx(() -> tokens.findByUser_Id(f.userId()).isPresent())).isTrue();
  }

  @Test
  void concurrentConfirmationsOnlyConsumeOnce() throws Exception {
    final Fixture f = fixture();
    final var ready = new CountDownLatch(2);
    final var start = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> action =
          () -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            try {
              verification.confirm(new ConfirmEmailVerificationRequest(f.raw()));
              return true;
            } catch (InvalidEmailVerificationTokenException expected) {
              return false;
            }
          };
      final var first = executor.submit(action);
      final var second = executor.submit(action);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(tx(() -> em.find(User.class, f.userId()).getEmailVerificationStatus()))
        .isEqualTo(EmailVerificationStatus.VERIFIED);
  }

  @Test
  void resendAndConfirmationSerializeWithoutConfirmingReplacedToken() throws Exception {
    final Fixture f = fixture();
    final var locked = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var resend =
          executor.submit(
              () ->
                  tx(
                      () -> {
                        em.find(
                            User.class,
                            f.userId(),
                            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                        locked.countDown();
                        await(release);
                        verification.resend(
                            new ResendEmailVerificationRequest(f.document(), f.institutionId()));
                        return null;
                      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      final var confirmation =
          executor.submit(() -> verification.confirm(new ConfirmEmailVerificationRequest(f.raw())));
      release.countDown();
      resend.get(15, TimeUnit.SECONDS);
      assertThatThrownBy(() -> confirmation.get(15, TimeUnit.SECONDS))
          .hasCauseInstanceOf(InvalidEmailVerificationTokenException.class);
    }
    assertThat(tx(() -> em.find(User.class, f.userId()).requiresEmailVerification())).isTrue();
  }

  @Test
  void concurrentResendsLeaveOneFreshToken() throws Exception {
    final Fixture f = fixture();
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var first =
          executor.submit(
              () ->
                  verification.resend(
                      new ResendEmailVerificationRequest(f.document(), f.institutionId())));
      final var second =
          executor.submit(
              () ->
                  verification.resend(
                      new ResendEmailVerificationRequest(f.document(), f.institutionId())));
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
    }
    assertThat(tx(() -> tokens.findByUser_Id(f.userId()).orElseThrow().getIssuedAt()))
        .isEqualTo(now);
    assertThat(
            tx(
                () ->
                    tokens
                        .findByTokenHash(RequestInstitutionalPasswordRecoveryUseCase.hash(f.raw()))
                        .isEmpty()))
        .isTrue();
  }

  @Test
  void correctionInvalidatesBothKindsOfOldLinkAndPreservesPendingState() {
    final Fixture f = fixture();
    tx(
        () -> {
          em.persist(
              InstitutionalPasswordResetToken.builder()
                  .user(em.find(User.class, f.userId()))
                  .tokenHash(RequestInstitutionalPasswordRecoveryUseCase.hash(f.raw() + "-reset"))
                  .expiresAt(now.plusSeconds(600))
                  .build());
          return null;
        });
    verification.changeEmail(
        new ChangePendingEmailRequest(
            f.institutionId(), f.document(), "correct-password", "correct@example.com"));
    assertThatThrownBy(() -> verification.confirm(new ConfirmEmailVerificationRequest(f.raw())))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
    assertThatThrownBy(
            () ->
                resetPassword.execute(
                    new ResetPasswordRequest(f.raw() + "-reset", "password123", "password123")))
        .isInstanceOf(InvalidPasswordRecoveryTokenException.class);
    assertThat(tx(() -> em.find(User.class, f.userId()).getPerson().getEmail()))
        .isEqualTo("correct@example.com");
    assertThat(tx(() -> em.find(User.class, f.userId()).requiresEmailVerification())).isTrue();
    verifyNoInteractions(sessions);
  }

  @Test
  void disabledAndDeletedAccountsPreserveUnusedTokenOnRejectedConfirmation() {
    final Fixture disabled = fixture();
    tx(
        () -> {
          em.find(User.class, disabled.userId()).updateAccess(false);
          return null;
        });
    assertThatThrownBy(
            () -> verification.confirm(new ConfirmEmailVerificationRequest(disabled.raw())))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
    assertThat(tx(() -> tokens.findByUser_Id(disabled.userId()).orElseThrow().getUsedAt()))
        .isNull();
    final Fixture deleted = fixture();
    tx(
        () -> {
          em.find(User.class, deleted.userId()).getPerson().delete();
          return null;
        });
    assertThatThrownBy(
            () -> verification.confirm(new ConfirmEmailVerificationRequest(deleted.raw())))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
    assertThat(tx(() -> tokens.findByUser_Id(deleted.userId()).orElseThrow().getUsedAt())).isNull();
  }

  @Test
  void pendingAccountCanResetPasswordWithoutBecomingVerified() {
    final Fixture f = fixture();
    tx(
        () -> {
          em.persist(
              InstitutionalPasswordResetToken.builder()
                  .user(em.find(User.class, f.userId()))
                  .tokenHash(RequestInstitutionalPasswordRecoveryUseCase.hash(f.raw() + "-reset"))
                  .expiresAt(now.plusSeconds(600))
                  .build());
          return null;
        });
    when(encoder.encode("password123")).thenReturn("new-hash");
    resetPassword.execute(
        new ResetPasswordRequest(f.raw() + "-reset", "password123", "password123"));
    assertThat(tx(() -> em.find(User.class, f.userId()).getPassword())).isEqualTo("new-hash");
    assertThat(tx(() -> em.find(User.class, f.userId()).isEnabled())).isFalse();
    verify(sessions).revokeInstitutionalSessionsForUser(f.userId());
  }

  @Test
  void correctionAndPasswordResetSerializeWithoutUsingOldRecipient() throws Exception {
    final Fixture f = fixture();
    final String rawReset = f.raw() + "-reset";
    tx(
        () -> {
          em.persist(
              InstitutionalPasswordResetToken.builder()
                  .user(em.find(User.class, f.userId()))
                  .tokenHash(RequestInstitutionalPasswordRecoveryUseCase.hash(rawReset))
                  .expiresAt(now.plusSeconds(600))
                  .build());
          return null;
        });
    final var locked = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var correction =
          executor.submit(
              () ->
                  tx(
                      () -> {
                        em.find(
                            User.class,
                            f.userId(),
                            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                        locked.countDown();
                        await(release);
                        verification.changeEmail(
                            new ChangePendingEmailRequest(
                                f.institutionId(),
                                f.document(),
                                "correct-password",
                                "correct@example.com"));
                        return null;
                      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      final var reset =
          executor.submit(
              () ->
                  resetPassword.execute(
                      new ResetPasswordRequest(rawReset, "password123", "password123")));
      release.countDown();
      correction.get(15, TimeUnit.SECONDS);
      assertThatThrownBy(() -> reset.get(15, TimeUnit.SECONDS))
          .hasCauseInstanceOf(InvalidPasswordRecoveryTokenException.class);
    }
    assertThat(tx(() -> em.find(User.class, f.userId()).getPassword()))
        .isEqualTo("encoded-password");
    verifyNoInteractions(sessions);
  }

  static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
