package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformRefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformLoginSessionPersistenceService {
  private final Clock clock;

  private final PlatformSessionRepository platformSessionRepository;
  private final PlatformRefreshTokenRepository platformRefreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final RefreshTokenGenerator refreshTokenGenerator;

  @Transactional
  public Result create(
      final UUID platformAccountId,
      final String ipAddress,
      final String userAgent,
      final boolean rememberMe) {
    final PlatformSession session =
        platformSessionRepository.save(
            PlatformSession.builder()
                .platformAccountId(platformAccountId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .rememberMe(rememberMe)
                .build());

    final String familyId = refreshTokenGenerator.newFamilyId();
    final GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate();
    platformRefreshTokenRepository.save(
        PlatformRefreshToken.builder()
            .platformSessionId(session.getId())
            .platformAccountId(platformAccountId)
            .tokenHash(generatedRefreshToken.tokenHash())
            .familyId(familyId)
            .expiresAt(clock.instant().plus(jwtProperties.refreshExpiration(rememberMe)))
            .build());

    return new Result(session.getId(), generatedRefreshToken.rawToken());
  }

  public record Result(UUID sessionId, String refreshToken) {}
}
