package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PlatformAuthenticationPolicy;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformLoginChallengeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformLoginResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.security.PlatformUsername;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlatformLoginUseCase {

  private final CredentialsAuthenticator credentialsAuthenticator;
  private final PlatformPasskeyStateStore state;
  private final PlatformPasskeyCredentialRepository credentials;
  private final PlatformAuthenticationPolicy policy;
  private final PlatformAuthenticationSessionIssuer sessionIssuer;

  public PlatformLoginResponse execute(
      final PlatformLoginRequest request, final HttpServletRequest httpRequest) {
    final String principal = PlatformUsername.format(request.email());

    final Authentication authentication =
        credentialsAuthenticator.authenticate(principal, request.password());

    if (!(authentication.getPrincipal() instanceof PlatformAccount account)) {
      throw new InvalidCredentialsException();
    }

    final boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
    if (!policy.passkeyRequired()) {
      return sessionIssuer.issue(account, rememberMe, httpRequest);
    }

    return new PlatformLoginChallengeResponse(
        state.createAttempt(account, rememberMe),
        !credentials.existsActiveByPlatformAccountId(account.getId()));
  }
}
