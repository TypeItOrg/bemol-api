package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
class LoginAttemptServiceRedisIntegrationTest {

  @Container
  // The Testcontainers lifecycle owns and stops this shared container.
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private LoginAttemptService service;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    final StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    service =
        new LoginAttemptService(
            org.mockito.Mockito.mock(InstitutionalHostContext.class),
            redisTemplate,
            new WebAuthnProperties(
                "localhost",
                "Boero",
                List.of("http://localhost:3000"),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                10,
                Duration.ofMinutes(5)));
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("Should let exactly one concurrent claim succeed per attempt")
  void claim_allowsExactlyOneConcurrentWinner() throws Exception {
    final LoginAttempt attempt = service.create(UUID.randomUUID(), UUID.randomUUID(), true);
    final int threads = 10;
    final ExecutorService executor = Executors.newFixedThreadPool(threads);
    final CountDownLatch ready = new CountDownLatch(threads);
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(threads);
    final AtomicInteger winners = new AtomicInteger();
    final ConcurrentLinkedQueue<UUID> winnerUserIds = new ConcurrentLinkedQueue<>();
    final List<Future<?>> claims = new ArrayList<>();

    try {
      for (int thread = 0; thread < threads; thread++) {
        claims.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  try {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    final LoginAttempt claimed = service.claim(attempt.id());
                    winners.incrementAndGet();
                    winnerUserIds.add(claimed.userId());
                  } catch (final InvalidLoginAttemptException expected) {
                    // concurrent loser
                  } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Concurrent login claim was interrupted", exception);
                  } finally {
                    done.countDown();
                  }
                }));
      }

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      for (final var claim : claims) {
        claim.get(10, TimeUnit.SECONDS);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
    }

    assertThat(winners.get()).isEqualTo(1);
    assertThat(winnerUserIds).containsExactly(attempt.userId());
    assertThatThrownBy(() -> service.resolve(attempt.id()))
        .isInstanceOf(InvalidLoginAttemptException.class);
  }

  @Test
  @DisplayName("Should keep the attempt available after reads without claiming")
  void resolve_doesNotConsumeAttempt() {
    final LoginAttempt attempt = service.create(UUID.randomUUID(), UUID.randomUUID(), false);

    assertThat(service.resolve(attempt.id()).userId()).isEqualTo(attempt.userId());
    assertThat(service.resolve(attempt.id()).userId()).isEqualTo(attempt.userId());
    assertThat(service.claim(attempt.id()).userId()).isEqualTo(attempt.userId());
  }
}
