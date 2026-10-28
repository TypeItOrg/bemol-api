package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.security.InstitutionalUsername;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReAuthenticateUseCase {

  private final UserRepository userRepository;
  private final CredentialsAuthenticator credentialsAuthenticator;
  private final RecentAuthService recentAuthService;

  public void execute(final JwtAuthenticatedUser principal, final String password) {
    final User user =
        userRepository
            .findWithPersonAndInstitutionById(principal.actorUserId())
            .orElseThrow(InvalidCredentialsException::new);
    final String username =
        InstitutionalUsername.format(user.getInstitutionId(), user.getDocumentNumber());
    credentialsAuthenticator.authenticate(username, password);
    recentAuthService.mark(
        principal.sessionId(),
        principal.actorUserId(),
        AuthenticationSessionIssuer.METHOD_PASSWORD);
    log.info("[Auth] Re-authentication succeeded, userId: {}", principal.actorUserId());
  }
}
