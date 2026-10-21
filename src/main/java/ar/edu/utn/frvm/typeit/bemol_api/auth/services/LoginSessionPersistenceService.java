package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginSessionPersistenceService {
  private final Clock clock;

  private final UserSessionRepository userSessionRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final RefreshTokenGenerator refreshTokenGenerator;

  @Transactional
  public Result create(
      final UUID userId, final String ipAddress, final String userAgent, final boolean rememberMe) {
    final UserSession session =
        userSessionRepository.save(
            UserSession.builder()
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .rememberMe(rememberMe)
                .build());

    final String familyId = refreshTokenGenerator.newFamilyId();
    final GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate();
    refreshTokenRepository.save(
        RefreshToken.builder()
            .sessionId(session.getId())
            .tokenHash(generatedRefreshToken.tokenHash())
            .familyId(familyId)
            .expiresAt(clock.instant().plus(jwtProperties.refreshExpiration(rememberMe)))
            .build());

    return new Result(session.getId(), generatedRefreshToken.rawToken());
  }

  public record Result(UUID sessionId, String refreshToken) {}
}
