package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.RefreshTokenReuseException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserSessionRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Import({
  RefreshTokenUseCase.class,
  SessionRevocationService.class,
  RefreshTokenGenerator.class,
  JpaAuditingTestConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@IntegrationTest
class RefreshTokenPostgresIntegrationTest {

  static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:17-alpine", "refresh_rotation");

  @Autowired private RefreshTokenUseCase useCase;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private InstitutionalHostContext hostContext;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private JwtProperties jwtProperties;
  @MockitoBean private CacheManager cacheManager;
  @MockitoBean private AuthorityResolver authorityResolver;
  @MockitoBean private RefreshReplayCache replayCache;

  @DynamicPropertySource
  static void postgresProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET TIME ZONE 'UTC'");
  }

  @Test
  @DisplayName("Should commit family and session revocation after refresh token reuse")
  void execute_commitsRevocationAfterReuseException() {
    final Fixture fixture = createFixture(true);

    assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(fixture.rawRefreshToken())))
        .isInstanceOf(RefreshTokenReuseException.class);

    inTransaction(
        () -> {
          assertThat(refreshTokenRepository.findByFamilyId(fixture.familyId()))
              .allMatch(mappedRefreshToken -> mappedRefreshToken.isRevoked());
          assertThat(userSessionRepository.findById(fixture.sessionId()))
              .get()
              .returns(false, mappedUserSession -> mappedUserSession.isActive());
          return null;
        });
  }

  @Test
  @DisplayName("Should serialize concurrent rotation and invalidate the reused token family")
  void execute_serializesConcurrentRotation() throws Exception {
    final Fixture fixture = createFixture(false);
    when(jwtProperties.refreshExpiration(false)).thenReturn(Duration.ofDays(7));
    when(jwtService.generateAccessToken(any(InstitutionalAccessTokenInput.class)))
        .thenReturn("access-token");
    final CountDownLatch ready = new CountDownLatch(2);
    final CountDownLatch start = new CountDownLatch(1);
    final Callable<Boolean> rotation =
        () -> {
          ready.countDown();
          assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
          try {
            useCase.execute(new RefreshTokenRequest(fixture.rawRefreshToken()));
            return true;
          } catch (RefreshTokenReuseException exception) {
            return false;
          }
        };

    try (var executor = Executors.newFixedThreadPool(2)) {
      final var first = executor.submit(rotation);
      final var second = executor.submit(rotation);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(
              java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }

    inTransaction(
        () -> {
          assertThat(refreshTokenRepository.findByFamilyId(fixture.familyId()))
              .allMatch(mappedRefreshToken -> mappedRefreshToken.isRevoked());
          assertThat(userSessionRepository.findById(fixture.sessionId()))
              .get()
              .returns(false, mappedUserSession -> mappedUserSession.isActive());
          return null;
        });
  }

  @Test
  @DisplayName("Should round-trip Instant values through PostgreSQL timestamptz")
  void persist_roundTripsInstantThroughTimestamptz() {
    final Instant expiresAt = Instant.parse("2026-12-01T18:45:30.123456Z");
    final Fixture fixture = persistFixture(false, expiresAt);

    inTransaction(
        () -> {
          entityManager.clear();
          final RefreshToken found =
              refreshTokenRepository
                  .findByTokenHash(JwtService.hashToken(fixture.rawRefreshToken()))
                  .orElseThrow();
          assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
          return null;
        });
  }

  private Fixture createFixture(final boolean revoked) {
    return persistFixture(revoked, Instant.now().plus(Duration.ofDays(7)));
  }

  private Fixture persistFixture(final boolean revoked, final Instant expiresAt) {
    return inTransaction(
        () -> {
          final String suffix = UUID.randomUUID().toString().substring(0, 8);
          final Institution institution =
              InstitutionalTestData.createInstitution(entityManager, "refresh-" + suffix);
          final String documentNumber =
              String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
          final User user =
              InstitutionalTestData.createUser(entityManager, institution, documentNumber);
          final UserSession session =
              UserSession.builder()
                  .userId(user.getId())
                  .ipAddress("192.0.2.10")
                  .userAgent("PostgreSQL integration test")
                  .active(true)
                  .build();
          entityManager.persist(session);
          final String rawToken = "refresh-" + UUID.randomUUID();
          final String familyId = UUID.randomUUID().toString();
          entityManager.persist(
              RefreshToken.builder()
                  .sessionId(session.getId())
                  .tokenHash(JwtService.hashToken(rawToken))
                  .familyId(familyId)
                  .expiresAt(expiresAt)
                  .revoked(revoked)
                  .build());
          return new Fixture(rawToken, familyId, session.getId());
        });
  }

  private <T> T inTransaction(final Supplier<T> action) {
    return new TransactionTemplate(transactionManager).execute(status -> action.get());
  }

  private record Fixture(String rawRefreshToken, String familyId, UUID sessionId) {}
}
