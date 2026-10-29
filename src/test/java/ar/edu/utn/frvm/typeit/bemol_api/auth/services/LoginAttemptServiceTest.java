package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
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
class LoginAttemptServiceTest {
  @Mock private InstitutionalHostContext hostContext;

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private LoginAttemptService service;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service =
        new LoginAttemptService(
            hostContext,
            redisTemplate,
            new WebAuthnProperties(
                "localhost",
                "Bemol",
                List.of("http://localhost:3000"),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                10,
                Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("Should persist the attempt with TTL and resolve it back")
  void create_resolveRoundTrip() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

    final LoginAttempt created = service.create(userId, institutionId, true);

    final String key = "bemol:auth:login-attempt:" + created.id();
    verify(valueOperations).set(eq(key), valueCaptor.capture(), eq(Duration.ofMinutes(5)));
    when(valueOperations.get(key)).thenReturn(valueCaptor.getValue());

    final LoginAttempt resolved = service.resolve(created.id());
    assertThat(resolved.userId()).isEqualTo(userId);
    assertThat(resolved.institutionId()).isEqualTo(institutionId);
    assertThat(resolved.hasActivePasskeys()).isTrue();
  }

  @Test
  @DisplayName("Should reject blank and unknown attempt ids")
  void resolve_rejectsInvalidIds() {
    when(valueOperations.get(anyString())).thenReturn(null);

    assertThatThrownBy(() -> service.resolve("unknown"))
        .isInstanceOf(InvalidLoginAttemptException.class);
    assertThatThrownBy(() -> service.resolve(" ")).isInstanceOf(InvalidLoginAttemptException.class);
  }

  @Test
  @DisplayName("Should delete the attempt on invalidate")
  void invalidate_deletesKey() {
    service.invalidate("attempt-id");

    verify(redisTemplate).delete("bemol:auth:login-attempt:attempt-id");
  }

  @Test
  @DisplayName("Should reject claim for blank and already consumed attempt ids")
  void claim_rejectsMissingIds() {
    when(valueOperations.get(anyString())).thenReturn(null);

    assertThatThrownBy(() -> service.claim("consumed"))
        .isInstanceOf(InvalidLoginAttemptException.class);
    assertThatThrownBy(() -> service.claim(" ")).isInstanceOf(InvalidLoginAttemptException.class);
  }
}
