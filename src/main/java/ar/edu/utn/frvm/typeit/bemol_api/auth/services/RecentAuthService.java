package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RecentAuthRequiredException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecentAuthService {

  private static final String KEY_PREFIX = "bemol:auth:recent:";

  private final StringRedisTemplate redisTemplate;
  private final WebAuthnProperties properties;

  public void mark(final UUID sessionId, final UUID userId, final String method) {
    final String value = userId + "|" + method + "|" + Instant.now().toEpochMilli();
    redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, value, properties.recentAuthTtl());
  }

  public boolean isRecent(final UUID sessionId, final UUID userId) {
    if (sessionId == null || userId == null) {
      return false;
    }
    final String value = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
    if (value == null || value.isBlank()) {
      return false;
    }
    final String[] parts = value.split("\\|", -1);
    if (parts.length != 3) {
      return false;
    }
    return parts[0].equals(userId.toString());
  }

  public void requireRecent(final UUID sessionId, final UUID userId) {
    if (!isRecent(sessionId, userId)) {
      throw new RecentAuthRequiredException();
    }
  }
}
