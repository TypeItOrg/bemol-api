package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationSessionIssuer {

  public static final String METHOD_PASSWORD = "PASSWORD";
  public static final String METHOD_PASSKEY = "PASSKEY";

  private final LoginSessionPersistenceService loginSessionPersistenceService;
  private final JwtService jwtService;
  private final AuthorityResolver authorityResolver;
  private final RecentAuthService recentAuthService;
  private final UserRepository userRepository;
  private final LoginAttemptService loginAttemptService;

  @Transactional
  public AuthResponse issuePassword(
      final LoginAttempt attempt,
      final User authenticated,
      final String ipAddress,
      final String userAgent,
      final boolean rememberMe) {
    final User user =
        userRepository
            .findWithPersonAndInstitutionById(attempt.userId())
            .orElseThrow(InvalidLoginAttemptException::new);
    final boolean sameAccount =
        user.getId().equals(authenticated.getId())
            && user.getInstitutionId().equals(attempt.institutionId());
    if (!sameAccount) {
      throw new InvalidLoginAttemptException();
    }
    if (!user.isEnabled() || !user.getPassword().equals(authenticated.getPassword())) {
      throw new InvalidCredentialsException();
    }

    loginAttemptService.claim(attempt.id());

    return issue(user, ipAddress, userAgent, rememberMe, METHOD_PASSWORD);
  }

  public AuthResponse issue(
      final User user,
      final String ipAddress,
      final String userAgent,
      final boolean rememberMe,
      final String method) {
    if (!user.isEnabled()) {
      throw new InvalidCredentialsException();
    }

    final var authorities =
        authorityResolver.resolveForPerson(user.getPerson().getId(), user.getInstitutionId());
    final LoginSessionPersistenceService.Result session =
        loginSessionPersistenceService.create(user.getId(), ipAddress, userAgent, rememberMe);
    final String accessToken =
        jwtService.generateAccessToken(
            InstitutionalAccessTokenInput.builder()
                .userId(user.getId())
                .personId(user.getPerson().getId())
                .institutionId(user.getInstitutionId())
                .documentNumber(user.getDocumentNumber())
                .sessionId(session.sessionId())
                .build());
    recentAuthService.mark(session.sessionId(), user.getId(), method);
    log.info(
        "[Auth] Login succeeded, userId: {}, institutionId: {}",
        user.getId(),
        user.getInstitutionId());

    return AuthResponse.of(
        user, user.getPerson().getId(), authorities, accessToken, session.refreshToken());
  }
}
