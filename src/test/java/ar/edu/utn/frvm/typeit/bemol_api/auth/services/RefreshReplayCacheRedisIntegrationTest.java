package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.RefreshReplayProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@IntegrationTest
class RefreshReplayCacheRedisIntegrationTest {

  private static final String ENCRYPTION_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private RefreshReplayCache replayCache;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    replayCache =
        new RefreshReplayCache(
            redisTemplate, new RefreshReplayProperties(ENCRYPTION_KEY, Duration.ofSeconds(30)));
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void encryptsReplaysAndIsolatesSessionRealmsAndExpirationForTheSameHash() {
    final String tokenHash = UUID.randomUUID().toString();
    final RefreshReplay platform = new RefreshReplay("platform-access", "platform-refresh");
    final RefreshReplay institutional =
        new RefreshReplay("institutional-access", "institutional-refresh");
    final String platformKey = "auth:refresh-replay:platform:" + tokenHash;
    final String institutionalKey = "auth:refresh-replay:institutional:" + tokenHash;

    assertThat(replayCache.get(AuthRealm.PLATFORM, tokenHash)).isEmpty();

    replayCache.put(AuthRealm.PLATFORM, tokenHash, platform);
    replayCache.put(AuthRealm.INSTITUTIONAL, tokenHash, institutional);

    assertThat(replayCache.get(AuthRealm.PLATFORM, tokenHash)).contains(platform);
    assertThat(replayCache.get(AuthRealm.INSTITUTIONAL, tokenHash)).contains(institutional);
    assertThat(redisTemplate.opsForValue().get(platformKey))
        .isNotNull()
        .doesNotContain(platform.accessToken(), platform.refreshToken());
    assertThat(redisTemplate.opsForValue().get(institutionalKey))
        .isNotNull()
        .doesNotContain(institutional.accessToken(), institutional.refreshToken());
    assertThat(redisTemplate.getExpire(platformKey, TimeUnit.MILLISECONDS))
        .isBetween(20_000L, 30_000L);
    assertThat(redisTemplate.getExpire(institutionalKey, TimeUnit.MILLISECONDS))
        .isBetween(20_000L, 30_000L);

    final var wrongKeyCache =
        new RefreshReplayCache(
            redisTemplate,
            new RefreshReplayProperties(
                Base64.getEncoder().encodeToString(new byte[32]), Duration.ofSeconds(30)));
    assertThatThrownBy(() -> wrongKeyCache.get(AuthRealm.PLATFORM, tokenHash))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(AuthMessages.REFRESH_REPLAY_CACHE_INVALID)
        .hasCauseInstanceOf(AEADBadTagException.class);

    assertThat(redisTemplate.expireAt(platformKey, Instant.EPOCH)).isTrue();

    assertThat(replayCache.get(AuthRealm.PLATFORM, tokenHash)).isEmpty();
    assertThat(replayCache.get(AuthRealm.INSTITUTIONAL, tokenHash)).contains(institutional);
  }
}
