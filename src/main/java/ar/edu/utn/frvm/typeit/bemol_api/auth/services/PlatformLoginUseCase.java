package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.security.PlatformUsername;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformLoginUseCase {

  private final CredentialsAuthenticator credentialsAuthenticator;
  private final PlatformLoginSessionPersistenceService platformLoginSessionPersistenceService;
  private final JwtService jwtService;

  public PlatformAuthResponse execute(
      final PlatformLoginRequest request, final HttpServletRequest httpRequest) {
    final String principal = PlatformUsername.format(request.email());

    final Authentication authentication =
        credentialsAuthenticator.authenticate(principal, request.password());

    if (!(authentication.getPrincipal() instanceof PlatformAccount account)) {
      throw new InvalidCredentialsException();
    }
    final boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
    final PlatformLoginSessionPersistenceService.Result session =
        platformLoginSessionPersistenceService.create(
            account.getId(),
            AuthRequestMetadata.clientIp(httpRequest),
            httpRequest.getHeader("User-Agent"),
            rememberMe);

    final String accessToken =
        jwtService.generatePlatformAccessToken(
            PlatformAccessTokenInput.builder()
                .platformAccountId(account.getId())
                .email(account.getEmail())
                .sessionId(session.sessionId())
                .build());
    log.info("[Auth] Platform login succeeded, platformAccountId: {}", account.getId());

    return PlatformAuthResponse.of(account, accessToken, session.refreshToken());
  }
}
