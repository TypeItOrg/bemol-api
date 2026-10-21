package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
  private final InstitutionalHostContext hostContext;

  private static final String KEY_PREFIX = "boero:auth:login-attempt:";

  private final StringRedisTemplate redisTemplate;
  private final WebAuthnProperties properties;

  public LoginAttempt create(
      final UUID userId, final UUID institutionId, final boolean hasActivePasskeys) {
    final String id = UUID.randomUUID().toString();
    final LoginAttempt attempt =
        new LoginAttempt(id, userId, institutionId, hasActivePasskeys, Instant.now());
    redisTemplate.opsForValue().set(key(id), serialize(attempt), properties.loginAttemptTtl());
    return attempt;
  }

  public LoginAttempt resolve(final String loginAttemptId) {
    if (loginAttemptId == null || loginAttemptId.isBlank()) {
      throw new InvalidLoginAttemptException();
    }
    final String value = redisTemplate.opsForValue().get(key(loginAttemptId));
    final var attempt =
        deserialize(loginAttemptId, Optional.ofNullable(value))
            .orElseThrow(InvalidLoginAttemptException::new);
    hostContext.requireInstitution(attempt.institutionId());
    return attempt;
  }

  public LoginAttempt claim(final String loginAttemptId) {
    if (loginAttemptId == null || loginAttemptId.isBlank()) {
      throw new InvalidLoginAttemptException();
    }
    resolve(loginAttemptId);
    final String value = redisTemplate.opsForValue().getAndDelete(key(loginAttemptId));
    final var attempt =
        deserialize(loginAttemptId, Optional.ofNullable(value))
            .orElseThrow(InvalidLoginAttemptException::new);
    hostContext.requireInstitution(attempt.institutionId());
    return attempt;
  }

  public void invalidate(final String loginAttemptId) {
    if (loginAttemptId == null || loginAttemptId.isBlank()) {
      return;
    }
    redisTemplate.delete(key(loginAttemptId));
  }

  private static String key(final String id) {
    return KEY_PREFIX + id;
  }

  private static String serialize(final LoginAttempt attempt) {
    return attempt.userId()
        + "|"
        + attempt.institutionId()
        + "|"
        + attempt.hasActivePasskeys()
        + "|"
        + attempt.createdAt().toEpochMilli();
  }

  private static Optional<LoginAttempt> deserialize(final String id, final Optional<String> value) {
    if (value.isEmpty() || value.get().isBlank()) {
      return Optional.empty();
    }
    final String[] parts = value.get().split("\\|", -1);
    final boolean invalidShape = parts.length != 4;
    if (invalidShape) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new LoginAttempt(
              id,
              UUID.fromString(parts[0]),
              UUID.fromString(parts[1]),
              Boolean.parseBoolean(parts[2]),
              Instant.ofEpochMilli(Long.parseLong(parts[3]))));
    } catch (IllegalArgumentException exception) {
      log.debug("[Auth] Invalid login attempt payload, attemptId: {}", id);
      return Optional.empty();
    }
  }
}
