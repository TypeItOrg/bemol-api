package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserSessionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {
  private final Clock clock;

  private final AccessTokenRevocationService accessTokenRevocationService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserSessionRepository userSessionRepository;

  @org.springframework.cache.annotation.CacheEvict(
      value = "activeSessions",
      key = "#principal.sessionId")
  @Transactional
  public void execute(JwtAuthenticatedUser principal, String accessToken) {
    accessTokenRevocationService.revoke(accessToken);

    refreshTokenRepository.revokeBySessionId(principal.sessionId());
    userSessionRepository
        .findById(principal.sessionId())
        .ifPresent(
            s -> {
              s.end(clock.instant());
              userSessionRepository.save(s);
              log.info(
                  "[Auth] Session ended successfully, userId: {}, sessionId: {}",
                  principal.actorUserId(),
                  principal.sessionId());
            });
  }
}
