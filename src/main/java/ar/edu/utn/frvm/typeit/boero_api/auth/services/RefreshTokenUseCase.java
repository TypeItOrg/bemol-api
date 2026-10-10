package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidRefreshTokenException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.RefreshTokenReuseException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserSessionRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
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
public class RefreshTokenUseCase {
  private final InstitutionalHostContext hostContext;
  private final Clock clock;

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserSessionRepository userSessionRepository;
  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final AuthorityResolver authorityResolver;
  private final RefreshReplayCache replayCache;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final SessionRevocationService sessionRevocationService;

  @Transactional(noRollbackFor = RefreshTokenReuseException.class)
  public AuthResponse execute(final RefreshTokenRequest request) {
    final String hash = JwtService.hashToken(request.refreshToken());
    final RefreshToken current =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

    if (hostContext.current().isPresent()) {
      final var ownerSession =
          userSessionRepository
              .findById(current.getSessionId())
              .orElseThrow(InvalidRefreshTokenException::new);
      final var owner =
          userRepository
              .findWithPersonAndInstitutionById(ownerSession.getUserId())
              .orElseThrow(InvalidRefreshTokenException::new);
      hostContext.requireInstitution(owner.getInstitutionId());
    }
    final Optional<RefreshReplay> replay = replayCache.get(AuthRealm.INSTITUTIONAL, hash);
    final RefreshRotationDecision decision =
        RefreshRotationPolicy.decide(
            current.isRevoked(), current.isExpiredAt(clock.instant()), replay);
    return switch (decision) {
      case RefreshRotationDecision.Replay replayDecision -> {
        final RefreshReplay tokens = replayDecision.tokens();
        final UserSession session = findActiveSession(current);
        final User user = findEnabledUser(session);
        yield createResponse(user, session, tokens.accessToken(), tokens.refreshToken());
      }
      case RefreshRotationDecision.Invalid() -> throw new InvalidRefreshTokenException();
      case RefreshRotationDecision.Reuse() -> {
        revokeReusedFamily(current);
        throw new RefreshTokenReuseException();
      }
      case RefreshRotationDecision.Rotate() -> rotate(current, hash);
    };
  }

  private AuthResponse rotate(final RefreshToken current, final String currentTokenHash) {
    final UserSession session = findActiveSession(current);
    final User user = findEnabledUser(session);

    current.revoke();
    refreshTokenRepository.save(current);

    final GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate();
    final RefreshToken next =
        RefreshToken.builder()
            .sessionId(session.getId())
            .tokenHash(generatedRefreshToken.tokenHash())
            .familyId(current.getFamilyId())
            .expiresAt(
                clock.instant().plus(jwtProperties.refreshExpiration(session.isRememberMe())))
            .build();
    refreshTokenRepository.save(next);

    final String accessToken =
        jwtService.generateAccessToken(
            InstitutionalAccessTokenInput.builder()
                .userId(user.getId())
                .personId(user.getPerson().getId())
                .institutionId(user.getInstitutionId())
                .documentNumber(user.getDocumentNumber())
                .sessionId(session.getId())
                .build());
    final AuthResponse response =
        createResponse(user, session, accessToken, generatedRefreshToken.rawToken());
    replayCache.put(
        AuthRealm.INSTITUTIONAL,
        currentTokenHash,
        new RefreshReplay(accessToken, generatedRefreshToken.rawToken()));
    return response;
  }

  private UserSession findActiveSession(final RefreshToken current) {
    final UserSession session =
        userSessionRepository
            .findById(current.getSessionId())
            .orElseThrow(InvalidRefreshTokenException::new);

    if (!session.isActive()) {
      throw new InvalidRefreshTokenException();
    }

    return session;
  }

  private User findEnabledUser(final UserSession session) {
    final User user =
        userRepository
            .findWithPersonAndInstitutionById(session.getUserId())
            .orElseThrow(InvalidRefreshTokenException::new);

    if (!user.isEnabled()) {
      throw new InvalidRefreshTokenException();
    }

    return user;
  }

  private AuthResponse createResponse(
      final User user,
      final UserSession session,
      final String accessToken,
      final String refreshToken) {
    return AuthResponse.of(
        user,
        user.getPerson().getId(),
        authorityResolver.resolveForPerson(user.getPerson().getId(), user.getInstitutionId()),
        accessToken,
        refreshToken);
  }

  private void revokeReusedFamily(final RefreshToken current) {
    final Set<UUID> sessionIds =
        refreshTokenRepository.findByFamilyId(current.getFamilyId()).stream()
            .map(mappedRefreshToken -> mappedRefreshToken.getSessionId())
            .collect(Collectors.toSet());
    refreshTokenRepository.revokeByFamilyId(current.getFamilyId());
    sessionRevocationService.revokeInstitutionalSessionsByIds(sessionIds);
  }
}
