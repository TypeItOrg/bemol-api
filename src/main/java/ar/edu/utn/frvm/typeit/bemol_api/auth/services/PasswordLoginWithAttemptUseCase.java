package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasswordLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.security.InstitutionalUsername;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordLoginWithAttemptUseCase {

  private final LoginAttemptService loginAttemptService;
  private final UserRepository userRepository;
  private final CredentialsAuthenticator credentialsAuthenticator;
  private final AuthenticationSessionIssuer sessionIssuer;

  public AuthResponse execute(
      final PasswordLoginRequest request, final HttpServletRequest httpRequest) {
    final LoginAttempt attempt = loginAttemptService.resolve(request.loginAttemptId());

    final User user =
        userRepository
            .findWithPersonAndInstitutionById(attempt.userId())
            .orElseThrow(InvalidLoginAttemptException::new);
    final boolean institutionMatches = user.getInstitutionId().equals(attempt.institutionId());
    if (!institutionMatches) {
      loginAttemptService.invalidate(attempt.id());
      throw new InvalidLoginAttemptException();
    }
    final String principal =
        InstitutionalUsername.format(user.getInstitutionId(), user.getDocumentNumber());
    final Authentication authentication =
        credentialsAuthenticator.authenticate(principal, request.password());
    if (!(authentication.getPrincipal() instanceof User authenticated)) {
      throw new InvalidCredentialsException();
    }
    if (!authenticated.isEnabled()) {
      throw new InvalidCredentialsException();
    }
    final boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
    return sessionIssuer.issuePassword(
        attempt,
        authenticated,
        AuthRequestMetadata.clientIp(httpRequest),
        httpRequest.getHeader("User-Agent"),
        rememberMe);
  }
}
