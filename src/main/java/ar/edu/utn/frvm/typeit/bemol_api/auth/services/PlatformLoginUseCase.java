package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PlatformAuthenticationPolicy;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformLoginChallengeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformLoginResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.security.PlatformUsername;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformLoginUseCase {

  private final CredentialsAuthenticator credentialsAuthenticator;
  private final PlatformPasskeyStateStore state;
  private final PlatformPasskeyCredentialRepository credentials;
  private final PlatformAuthenticationPolicy policy;
  private final PlatformAuthenticationSessionIssuer sessionIssuer;
  private final PlatformAccountRepository accounts;
  private final EntityManager entityManager;

  @Transactional
  public PlatformLoginResponse execute(
      final PlatformLoginRequest request, final HttpServletRequest httpRequest) {
    final String principal = PlatformUsername.format(request.email());

    final Authentication authentication =
        credentialsAuthenticator.authenticate(principal, request.password());

    final var authenticated =
        CurrentPrincipal.of(authentication, PlatformAccount.class)
            .orElseThrow(InvalidCredentialsException::new);

    final String authenticatedPassword = authenticated.getPassword();
    final String authenticatedEmail = authenticated.getEmail();
    final PlatformAccount account =
        accounts
            .findWithLockById(authenticated.getId())
            .orElseThrow(InvalidCredentialsException::new);
    entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
    if (!account.isEnabled()
        || !account.getPassword().equals(authenticatedPassword)
        || !account.getEmail().equals(authenticatedEmail)) {
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
