package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformLogoutUseCase {
  private final Clock clock;

  private final AccessTokenRevocationService accessTokenRevocationService;
  private final PlatformRefreshTokenRepository platformRefreshTokenRepository;
  private final PlatformSessionRepository platformSessionRepository;

  @org.springframework.cache.annotation.CacheEvict(
      value = "activePlatformSessions",
      key = "#principal.sessionId")
  @Transactional
  public void execute(JwtAuthenticatedPlatformAccount principal, String accessToken) {
    accessTokenRevocationService.revoke(accessToken);

    platformRefreshTokenRepository.revokeByPlatformSessionId(principal.sessionId());
    platformSessionRepository
        .findById(principal.sessionId())
        .ifPresent(
            session -> {
              session.end(clock.instant());
              platformSessionRepository.save(session);
            });
  }
}
