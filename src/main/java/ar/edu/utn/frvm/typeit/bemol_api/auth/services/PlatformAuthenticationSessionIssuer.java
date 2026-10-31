package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuthenticationSessionIssuer {
  private final PlatformLoginSessionPersistenceService sessions;
  private final JwtService jwt;

  @Transactional
  public PlatformAuthResponse issue(
      final PlatformAccount account, final boolean rememberMe, final HttpServletRequest request) {
    final var session =
        sessions.create(
            account.getId(),
            AuthRequestMetadata.clientIp(request),
            request.getHeader("User-Agent"),
            rememberMe);
    final String token =
        jwt.generatePlatformAccessToken(
            PlatformAccessTokenInput.builder()
                .platformAccountId(account.getId())
                .email(account.getEmail())
                .sessionId(session.sessionId())
                .build());
    log.info("[Auth] Platform login succeeded, platformAccountId: {}", account.getId());
    return PlatformAuthResponse.of(account, token, session.refreshToken());
  }
}
