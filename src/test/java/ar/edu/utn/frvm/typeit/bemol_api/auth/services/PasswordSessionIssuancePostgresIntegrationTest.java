package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PlatformAuthenticationPolicy;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ResetPasswordRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalAuthoritySnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  JpaAuditingTestConfig.class,
  AuthenticationSessionIssuer.class,
  PlatformLoginUseCase.class,
  PlatformAuthenticationSessionIssuer.class,
  LoginSessionPersistenceService.class,
  PlatformLoginSessionPersistenceService.class,
  RefreshTokenGenerator.class,
  SessionRevocationService.class,
  ResetInstitutionalPasswordUseCase.class,
  UpdatePlatformAccountUseCase.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@IntegrationTest
class PasswordSessionIssuancePostgresIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
  private static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:18-alpine", "password_session_issuance");

  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private JdbcTemplate database;
  @Autowired private UserRepository users;
  @Autowired private PlatformAccountRepository accounts;
  @Autowired private AuthenticationSessionIssuer issuer;
  @Autowired private PlatformLoginUseCase platformLogin;
  @Autowired private ResetInstitutionalPasswordUseCase resetPassword;
  @Autowired private UpdatePlatformAccountUseCase updatePlatformAccount;
  @MockitoSpyBean private LoginSessionPersistenceService institutionalPersistence;
  @MockitoSpyBean private PlatformLoginSessionPersistenceService platformPersistence;

  @MockitoBean private PlatformAuthenticationPolicy platformPolicy;
  @MockitoBean private PlatformPasskeyStateStore passkeyState;
  @MockitoBean private PlatformPasskeyCredentialRepository passkeys;
  @MockitoBean private Clock clock;
  @MockitoBean private JwtProperties jwtProperties;
  @MockitoBean private JwtService jwt;
  @MockitoBean private AuthorityResolver authorities;
  @MockitoBean private RecentAuthService recentAuth;
  @MockitoBean private LoginAttemptService attempts;
  @MockitoBean private CredentialsAuthenticator credentials;
  @MockitoBean private AuditEventRecorder audit;
  @MockitoBean private InstitutionalHostContext hostContext;
  @MockitoBean private PasswordEncoder encoder;
  @MockitoBean private CacheManager cacheManager;

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
    when(jwtProperties.refreshExpiration(anyBoolean())).thenReturn(Duration.ofDays(7));
    when(jwt.generateAccessToken(any())).thenReturn("institutional-access");
    when(jwt.generatePlatformAccessToken(any())).thenReturn("platform-access");
    when(encoder.encode("new-password")).thenReturn("new-hash");
    when(authorities.resolvePersonAuthorities(any(), any()))
        .thenReturn(new InstitutionalAuthoritySnapshot(Set.of(), List.of()));
  }

  @ParameterizedTest
  @EnumSource(AuthRealm.class)
  void committedCredentialChangeRejectsAnAlreadyManagedOldPasswordSnapshot(final AuthRealm realm)
      throws Exception {
    final Fixture fixture = fixture();
    configurePlatformAuthentication(fixture);
    final var loaded = new CountDownLatch(1);
    final var resume = new CountDownLatch(1);
    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var login =
          executor.submit(
              () ->
                  tx(
                      () -> {
                        final User authenticated =
                            users.findWithPersonAndInstitutionById(fixture.userId()).orElseThrow();
                        accounts.findById(fixture.platformId()).orElseThrow();
                        loaded.countDown();
                        await(resume);
                        return login(realm, fixture, authenticated);
                      }));
      try {
        assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
        changeCredentials(realm, fixture);
        resume.countDown();

        assertThatThrownBy(() -> login.get(10, TimeUnit.SECONDS))
            .hasCauseInstanceOf(InvalidCredentialsException.class);
      } finally {
        resume.countDown();
      }
    }

    assertActiveSessions(realm, fixture, 0);
    verify(attempts, never()).claim(anyString());
    final User current =
        tx(() -> users.findWithPersonAndInstitutionById(fixture.userId()).orElseThrow());
    assertThat(login(realm, fixture, current)).isNotBlank();
    assertActiveSessions(realm, fixture, 1);
  }

  @ParameterizedTest
  @EnumSource(AuthRealm.class)
  void issuanceHoldsTheAccountLockUntilTheSessionAndRefreshFamilyCommit(final AuthRealm realm)
      throws Exception {
    final Fixture fixture = fixture();
    configurePlatformAuthentication(fixture);
    final User authenticated =
        tx(() -> users.findWithPersonAndInstitutionById(fixture.userId()).orElseThrow());
    final var checked = new CountDownLatch(1);
    final var resume = new CountDownLatch(1);
    final var changing = new CountDownLatch(1);
    if (realm == AuthRealm.INSTITUTIONAL) {
      doAnswer(invocation -> pauseBeforeInsert(realm, fixture, checked, resume, invocation))
          .when(institutionalPersistence)
          .create(eq(fixture.userId()), anyString(), anyString(), anyBoolean());
    } else {
      doAnswer(invocation -> pauseBeforeInsert(realm, fixture, checked, resume, invocation))
          .when(platformPersistence)
          .create(eq(fixture.platformId()), anyString(), anyString(), anyBoolean());
    }

    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var login = executor.submit(() -> login(realm, fixture, authenticated));
      try {
        assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
        final var change =
            executor.submit(
                () -> {
                  changing.countDown();
                  changeCredentials(realm, fixture);
                });
        assertThat(changing.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> change.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        resume.countDown();
        assertThat(login.get(10, TimeUnit.SECONDS)).isNotBlank();
        change.get(10, TimeUnit.SECONDS);
      } finally {
        resume.countDown();
      }
    }

    assertActiveSessions(realm, fixture, 0);
    final String refreshQuery =
        realm == AuthRealm.INSTITUTIONAL
            ? "SELECT count(*) FROM refresh_tokens r JOIN user_sessions s ON s.user_session_id = r.session_id WHERE s.user_id = ? AND NOT r.revoked"
            : "SELECT count(*) FROM platform_refresh_tokens WHERE platform_account_id = ? AND NOT revoked";
    assertThat(database.queryForObject(refreshQuery, Long.class, accountId(realm, fixture)))
        .isZero();
    final String sessionsQuery =
        realm == AuthRealm.INSTITUTIONAL
            ? "SELECT count(*) FROM user_sessions WHERE user_id = ?"
            : "SELECT count(*) FROM platform_sessions WHERE platform_account_id = ?";
    assertThat(database.queryForObject(sessionsQuery, Long.class, accountId(realm, fixture)))
        .isEqualTo(1L);
  }

  private Object pauseBeforeInsert(
      final AuthRealm realm,
      final Fixture fixture,
      final CountDownLatch checked,
      final CountDownLatch resume,
      final InvocationOnMock invocation)
      throws Throwable {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    final Object account =
        realm == AuthRealm.INSTITUTIONAL
            ? entityManager.find(User.class, fixture.userId())
            : entityManager.find(PlatformAccount.class, fixture.platformId());
    assertThat(entityManager.getLockMode(account)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    checked.countDown();
    await(resume);
    return invocation.callRealMethod();
  }

  private String login(final AuthRealm realm, final Fixture fixture, final User authenticated) {
    if (realm == AuthRealm.INSTITUTIONAL) {
      final var attempt =
          new LoginAttempt(
              "attempt-" + UUID.randomUUID(),
              fixture.userId(),
              fixture.institutionId(),
              false,
              NOW);
      return issuer
          .issuePassword(attempt, authenticated, "192.0.2.10", "test", false)
          .tokens()
          .refreshToken();
    }
    final var http = new MockHttpServletRequest();
    http.setRemoteAddr("192.0.2.10");
    http.addHeader("User-Agent", "test");
    return ((PlatformAuthResponse)
            platformLogin.execute(
                new PlatformLoginRequest(fixture.email(), "verified-password", false), http))
        .tokens()
        .refreshToken();
  }

  private void configurePlatformAuthentication(final Fixture fixture) {
    when(credentials.authenticate(anyString(), anyString()))
        .thenAnswer(
            invocation ->
                new TestingAuthenticationToken(
                    entityManager.find(PlatformAccount.class, fixture.platformId()), ""));
  }

  private void changeCredentials(final AuthRealm realm, final Fixture fixture) {
    if (realm == AuthRealm.INSTITUTIONAL) {
      resetPassword.execute(
          new ResetPasswordRequest(fixture.resetToken(), "new-password", "new-password"));
    } else {
      updatePlatformAccount.execute(
          fixture.platformId(),
          new UpdatePlatformAccountRequest("Test", "Owner", fixture.email(), "new-password"));
    }
  }

  private void assertActiveSessions(
      final AuthRealm realm, final Fixture fixture, final long count) {
    final String query =
        realm == AuthRealm.INSTITUTIONAL
            ? "SELECT count(*) FROM user_sessions WHERE user_id = ? AND active"
            : "SELECT count(*) FROM platform_sessions WHERE platform_account_id = ? AND active";
    assertThat(database.queryForObject(query, Long.class, accountId(realm, fixture)))
        .isEqualTo(count);
  }

  private UUID accountId(final AuthRealm realm, final Fixture fixture) {
    return realm == AuthRealm.INSTITUTIONAL ? fixture.userId() : fixture.platformId();
  }

  private Fixture fixture() {
    return tx(
        () -> {
          final var institution = createInstitution(entityManager, "login-" + UUID.randomUUID());
          final User user = createUser(entityManager, institution, "12345678");
          user.changePassword("old-hash");
          final String resetToken = "reset-" + UUID.randomUUID();
          entityManager.persist(
              InstitutionalPasswordResetToken.builder()
                  .user(user)
                  .tokenHash(RequestInstitutionalPasswordRecoveryUseCase.hash(resetToken))
                  .expiresAt(NOW.plusSeconds(600))
                  .build());
          final PlatformAccount platform =
              PlatformAccount.builder()
                  .email(UUID.randomUUID() + "@example.com")
                  .name("Test")
                  .lastName("Owner")
                  .password("old-hash")
                  .build();
          entityManager.persist(platform);
          entityManager.flush();
          return new Fixture(
              user.getId(), institution.getId(), platform.getId(), platform.getEmail(), resetToken);
        });
  }

  private <T> T tx(final Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  private static void await(final CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the credential transaction");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private record Fixture(
      UUID userId, UUID institutionId, UUID platformId, String email, String resetToken) {}
}
