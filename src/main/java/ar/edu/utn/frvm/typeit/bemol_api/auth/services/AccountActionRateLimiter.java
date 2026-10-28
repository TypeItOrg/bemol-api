package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.EmailVerificationCooldownException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountActionRateLimiter {
  private static final DefaultRedisScript<Long> COUNT =
      new DefaultRedisScript<>(
          "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end; return n",
          Long.class);
  private final StringRedisTemplate redis;

  public void guardianRequest(final UUID institutionId, final UUID actorId) {
    if (!isAllowed("guardian:" + institutionId + ":" + actorId, 5)) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions
          .GuardianRequestLimitException();
    }
  }

  public void mail(final UUID personId) {
    if (!isAllowed("mail:" + personId, 1)) {
      throw new EmailVerificationCooldownException();
    }
  }

  private boolean isAllowed(final String key, final int maximum) {
    final Long count = redis.execute(COUNT, List.of("bemol:auth:action:" + key));
    return count != null && count <= maximum;
  }
}
