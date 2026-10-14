package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.RecentAuthRequiredException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RecentAuthServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RecentAuthService service;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service =
        new RecentAuthService(
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

  @Test
  @DisplayName("Should accept a marker bound to the same session and user")
  void isRecent_acceptsMatchingMarker() {
    final UUID sessionId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    when(valueOperations.get("boero:auth:recent:" + sessionId)).thenReturn(userId + "|PASSWORD|1");

    assertThat(service.isRecent(sessionId, userId)).isTrue();
  }

  @Test
  @DisplayName("Should reject markers from other users or missing markers")
  void isRecent_rejectsMismatchOrMissing() {
    final UUID sessionId = UUID.randomUUID();
    when(valueOperations.get(anyString())).thenReturn(UUID.randomUUID() + "|PASSWORD|1");

    assertThat(service.isRecent(sessionId, UUID.randomUUID())).isFalse();

    when(valueOperations.get(anyString())).thenReturn(null);

    assertThatThrownBy(() -> service.requireRecent(sessionId, UUID.randomUUID()))
        .isInstanceOf(RecentAuthRequiredException.class);
  }

  @Test
  @DisplayName("Should store the marker with TTL")
  void mark_setsWithTtl() {
    final UUID sessionId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    final UUID userId = UUID.fromString("00000000-0000-4000-8000-000000000002");
    final long before = java.time.Instant.now().toEpochMilli();

    service.mark(sessionId, userId, "PASSWORD");

    final var value = ArgumentCaptor.forClass(String.class);
    verify(valueOperations)
        .set(eq("boero:auth:recent:" + sessionId), value.capture(), eq(Duration.ofMinutes(5)));
    final var parts = value.getValue().split("\\|", -1);
    assertThat(parts).hasSize(3);
    assertThat(parts[0]).isEqualTo(userId.toString());
    assertThat(parts[1]).isEqualTo("PASSWORD");
    assertThat(Long.parseLong(parts[2])).isBetween(before, java.time.Instant.now().toEpochMilli());
  }
}
