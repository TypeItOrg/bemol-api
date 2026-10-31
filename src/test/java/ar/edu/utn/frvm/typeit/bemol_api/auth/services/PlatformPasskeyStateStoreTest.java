package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class PlatformPasskeyStateStoreTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final UUID accountId = UUID.randomUUID();
  private final PlatformPasskeyStateStore store =
      new PlatformPasskeyStateStore(
          redis,
          mapper,
          new WebAuthnProperties(
              "localhost",
              "Bemol",
              List.of("http://localhost:3000"),
              Duration.ofMinutes(5),
              Duration.ofMinutes(5),
              10,
              Duration.ofMinutes(5)));

  @BeforeEach
  void setup() {
    when(redis.opsForValue()).thenReturn(values);
  }

  @Test
  void setupCodeIsBoundToAccountAndStoredOnlyAsHash() {
    final String code = "a".repeat(64);
    final String hash = JwtService.hashToken(code);
    when(values.get("bemol:platform:setup:" + accountId)).thenReturn(hash);
    assertThat(store.requireSetupGrant(accountId, code)).isEqualTo(hash).isNotEqualTo(code);
    assertThatThrownBy(() -> store.requireSetupGrant(UUID.randomUUID(), code))
        .isInstanceOf(WebAuthnVerificationFailedException.class);
    assertThatThrownBy(() -> store.requireSetupGrant(accountId, "b".repeat(64)))
        .isInstanceOf(WebAuthnVerificationFailedException.class);
  }

  @Test
  void expiredOrReplacedSetupGrantCannotBeConsumed() {
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any()))
        .thenReturn(0L);
    assertThatThrownBy(() -> store.consumeSetupGrant(accountId, "old-hash"))
        .isInstanceOf(WebAuthnVerificationFailedException.class);
  }

  @Test
  void ceremonyIsConsumedOnceAndCannotChangePurpose() {
    final var ceremony =
        new PlatformPasskeyStateStore.Ceremony(
            accountId, null, "attempt", "login", "", "", "", "{}");
    when(values.getAndDelete("bemol:platform:ceremony:nonce"))
        .thenReturn(mapper.writeValueAsString(ceremony))
        .thenReturn((String) null);
    assertThatThrownBy(() -> store.consumeCeremony("nonce", "setup"))
        .isInstanceOf(InvalidLoginAttemptException.class);
    assertThatThrownBy(() -> store.consumeCeremony("nonce", "login"))
        .isInstanceOf(InvalidLoginAttemptException.class);
    verify(values, times(2)).getAndDelete("bemol:platform:ceremony:nonce");
  }

  @Test
  void recentProofIsBoundToSessionAndOperationAndConsumed() {
    final UUID session = UUID.randomUUID();
    when(values.getAndDelete("bemol:platform:recent:" + accountId + ":" + session + ":register"))
        .thenReturn("verified")
        .thenReturn((String) null);
    store.requireRecent(accountId, session, "register", true);
    assertThatThrownBy(() -> store.requireRecent(accountId, session, "register", true))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RecentAuthRequiredException.class);
    assertThatThrownBy(
            () -> store.requireRecent(accountId, session, "revoke:" + UUID.randomUUID(), true))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RecentAuthRequiredException.class);
  }
}
