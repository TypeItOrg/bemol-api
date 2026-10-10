package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PlatformRefreshToken;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PlatformSession;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidRefreshTokenException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.RefreshTokenReuseException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformSessionRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PlatformAuthResponse;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformRefreshTokenUseCase {
  private final Clock clock;

  private final PlatformRefreshTokenRepository platformRefreshTokenRepository;
  private final PlatformSessionRepository platformSessionRepository;
  private final PlatformAccountRepository platformAccountRepository;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final RefreshReplayCache replayCache;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final SessionRevocationService sessionRevocationService;

  @Transactional(noRollbackFor = RefreshTokenReuseException.class)
  public PlatformAuthResponse execute(final RefreshTokenRequest request) {
    final String hash = JwtService.hashToken(request.refreshToken());
    final PlatformRefreshToken current =
        platformRefreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(InvalidRefreshTokenException::new);

    final Optional<RefreshReplay> replay = replayCache.get(AuthRealm.PLATFORM, hash);
    final RefreshRotationDecision decision =
        RefreshRotationPolicy.decide(
            current.isRevoked(), current.isExpiredAt(clock.instant()), replay);
    return switch (decision) {
      case RefreshRotationDecision.Replay replayDecision -> {
        final RefreshReplay tokens = replayDecision.tokens();
        findActiveSession(current);
        final PlatformAccount account = findEnabledAccount(current);
        yield PlatformAuthResponse.of(account, tokens.accessToken(), tokens.refreshToken());
      }
      case RefreshRotationDecision.Invalid() -> throw new InvalidRefreshTokenException();
      case RefreshRotationDecision.Reuse() -> {
        revokeReusedFamily(current);
        throw new RefreshTokenReuseException();
      }
      case RefreshRotationDecision.Rotate() -> rotate(current, hash);
    };
  }

  private PlatformAuthResponse rotate(
      final PlatformRefreshToken current, final String currentTokenHash) {
    final PlatformSession session = findActiveSession(current);
    final PlatformAccount account = findEnabledAccount(current);

    current.revoke();
    platformRefreshTokenRepository.save(current);

    final GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate();
    final PlatformRefreshToken next =
        PlatformRefreshToken.builder()
            .platformSessionId(session.getId())
            .platformAccountId(account.getId())
            .tokenHash(generatedRefreshToken.tokenHash())
            .familyId(current.getFamilyId())
            .expiresAt(
                clock.instant().plus(jwtProperties.refreshExpiration(session.isRememberMe())))
            .build();
    platformRefreshTokenRepository.save(next);

    final String accessToken =
        jwtService.generatePlatformAccessToken(
            PlatformAccessTokenInput.builder()
                .platformAccountId(account.getId())
                .email(account.getEmail())
                .sessionId(session.getId())
                .build());
    final PlatformAuthResponse response =
        PlatformAuthResponse.of(account, accessToken, generatedRefreshToken.rawToken());
    replayCache.put(
        AuthRealm.PLATFORM,
        currentTokenHash,
        new RefreshReplay(accessToken, generatedRefreshToken.rawToken()));
    return response;
  }

  private PlatformSession findActiveSession(final PlatformRefreshToken current) {
    final PlatformSession session =
        platformSessionRepository
            .findById(current.getPlatformSessionId())
            .orElseThrow(InvalidRefreshTokenException::new);

    if (!session.isActive()) {
      throw new InvalidRefreshTokenException();
    }

    if (!session.getPlatformAccountId().equals(current.getPlatformAccountId())) {
      throw new InvalidRefreshTokenException();
    }

    return session;
  }

  private PlatformAccount findEnabledAccount(final PlatformRefreshToken current) {
    final PlatformAccount account =
        platformAccountRepository
            .findById(current.getPlatformAccountId())
            .orElseThrow(InvalidRefreshTokenException::new);

    if (!account.isEnabled()) {
      throw new InvalidRefreshTokenException();
    }

    return account;
  }

  private void revokeReusedFamily(final PlatformRefreshToken current) {
    final Set<UUID> sessionIds =
        platformRefreshTokenRepository.findByFamilyId(current.getFamilyId()).stream()
            .map(mappedPlatformRefreshToken -> mappedPlatformRefreshToken.getPlatformSessionId())
            .collect(Collectors.toSet());
    platformRefreshTokenRepository.revokeByFamilyId(current.getFamilyId());
    sessionRevocationService.revokePlatformSessionsByIds(sessionIds);
  }
}
