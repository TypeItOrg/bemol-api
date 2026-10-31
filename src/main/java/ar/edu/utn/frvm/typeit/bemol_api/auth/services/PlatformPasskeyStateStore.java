package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PlatformPasskeyStateStore {
  private static final String PREFIX = "bemol:platform:";
  private static final DefaultRedisScript<Long> CONSUME =
      new DefaultRedisScript<>(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end; return 0",
          Long.class);
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end; return n",
          Long.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final WebAuthnProperties properties;

  public record Attempt(UUID accountId, String passwordHash, boolean rememberMe) {}

  public record Ceremony(
      UUID accountId,
      @Nullable String sessionId,
      @Nullable String attemptId,
      String purpose,
      String target,
      String grant,
      String label,
      String options) {}

  public String createAttempt(final PlatformAccount account, final boolean rememberMe) {
    limit(account.getId());
    return store(
        "attempt:",
        new Attempt(account.getId(), JwtService.hashToken(account.getPassword()), rememberMe));
  }

  public Attempt attempt(final String id) {
    return read(redis.opsForValue().get(PREFIX + "attempt:" + id), Attempt.class);
  }

  public Attempt claimAttempt(final String id) {
    return read(redis.opsForValue().getAndDelete(PREFIX + "attempt:" + id), Attempt.class);
  }

  public String storeCeremony(final Ceremony ceremony) {
    return store("ceremony:", ceremony);
  }

  public Ceremony consumeCeremony(final String id, final String purpose) {
    final Ceremony ceremony =
        read(redis.opsForValue().getAndDelete(PREFIX + "ceremony:" + id), Ceremony.class);
    if (!purpose.equals(ceremony.purpose())) {
      throw new InvalidLoginAttemptException();
    }
    return ceremony;
  }

  public String requireSetupGrant(final UUID accountId, final String code) {
    final String hash = JwtService.hashToken(code);
    final String stored = redis.opsForValue().get(PREFIX + "setup:" + accountId);
    if (stored == null
        || !MessageDigest.isEqual(
            stored.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
      throw new WebAuthnVerificationFailedException();
    }
    return hash;
  }

  public void consumeSetupGrant(final UUID accountId, final String expected) {
    if (!Long.valueOf(1)
        .equals(redis.execute(CONSUME, List.of(PREFIX + "setup:" + accountId), expected))) {
      throw new WebAuthnVerificationFailedException();
    }
  }

  public void grantRecent(final UUID accountId, final UUID sessionId, final String target) {
    redis
        .opsForValue()
        .set(recentKey(accountId, sessionId, target), "verified", properties.recentAuthTtl());
  }

  public void requireRecent(
      final UUID accountId, final UUID sessionId, final String target, final boolean consume) {
    final String key = recentKey(accountId, sessionId, target);
    final String value =
        consume ? redis.opsForValue().getAndDelete(key) : redis.opsForValue().get(key);
    if (value == null) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RecentAuthRequiredException();
    }
  }

  public void limit(final UUID accountId) {
    final Long count = redis.execute(LIMIT, List.of(PREFIX + "attempt-limit:" + accountId));
    if (count == null || count > 10) {
      throw new WebAuthnVerificationFailedException();
    }
  }

  private String recentKey(final UUID accountId, final UUID sessionId, final String target) {
    return PREFIX + "recent:" + accountId + ":" + sessionId + ":" + target;
  }

  private String store(final String kind, final Object value) {
    final String id = UUID.randomUUID().toString();
    redis
        .opsForValue()
        .set(
            PREFIX + kind + id,
            mapper.writeValueAsString(value),
            kind.equals("attempt:") ? properties.loginAttemptTtl() : properties.challengeTtl());
    return id;
  }

  private <T> T read(final @Nullable String json, final Class<T> type) {
    if (json == null) {
      throw new InvalidLoginAttemptException();
    }
    try {
      return mapper.readValue(json, type);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new InvalidLoginAttemptException();
    }
  }
}
