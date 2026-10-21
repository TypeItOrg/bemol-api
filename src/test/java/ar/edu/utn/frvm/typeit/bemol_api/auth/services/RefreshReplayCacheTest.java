package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.RefreshReplayProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RefreshReplayCacheTest {

  private static final String ENCRYPTION_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RefreshReplayCache replayCache;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    replayCache =
        new RefreshReplayCache(
            redisTemplate, new RefreshReplayProperties(ENCRYPTION_KEY, Duration.ofSeconds(5)));
  }

  @Test
  void putAndGetRoundTripTheEncryptedReplay() {
    final String tokenHash = "previous-token-hash";
    final RefreshReplay replay = new RefreshReplay("access-token", "refresh-token");
    final ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

    replayCache.put(AuthRealm.PLATFORM, tokenHash, replay);

    verify(valueOperations).set(any(), valueCaptor.capture(), any(Duration.class));
    when(valueOperations.get(any())).thenReturn(valueCaptor.getValue());

    assertThat(replayCache.get(AuthRealm.PLATFORM, tokenHash)).contains(replay);
    assertThat(valueCaptor.getValue())
        .doesNotContain("access-token")
        .doesNotContain("refresh-token");
  }

  @Test
  void putAndGetRoundTripTheInstitutionalReplay() {
    final String tokenHash = "previous-institutional-token-hash";
    final RefreshReplay replay =
        new RefreshReplay("institutional-access-token", "institutional-refresh-token");
    final ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

    replayCache.put(AuthRealm.INSTITUTIONAL, tokenHash, replay);

    verify(valueOperations).set(any(), valueCaptor.capture(), any(Duration.class));
    when(valueOperations.get(any())).thenReturn(valueCaptor.getValue());

    assertThat(replayCache.get(AuthRealm.INSTITUTIONAL, tokenHash)).contains(replay);
    assertThat(valueCaptor.getValue())
        .doesNotContain("institutional-access-token")
        .doesNotContain("institutional-refresh-token");
  }
}
