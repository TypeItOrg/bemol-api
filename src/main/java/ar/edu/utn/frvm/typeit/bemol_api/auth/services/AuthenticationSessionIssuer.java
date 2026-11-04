package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
  public static final String METHOD_AUTO_LOGIN = "AUTO_LOGIN";

  private final LoginSessionPersistenceService loginSessionPersistenceService;
  private final JwtService jwtService;
  private final AuthorityResolver authorityResolver;
  private final RecentAuthService recentAuthService;
  private final UserRepository userRepository;
  private final LoginAttemptService loginAttemptService;
  private final EntityManager entityManager;

  @Transactional
  public AuthResponse issuePassword(
      final LoginAttempt attempt,
      final User authenticated,
      final String ipAddress,
      final String userAgent,
      final boolean rememberMe) {
    final String authenticatedPassword = authenticated.getPassword();
    final User user =
        userRepository
            .findWithLockById(attempt.userId())
            .orElseThrow(InvalidLoginAttemptException::new);
    // A locking query can return an account already present in this persistence context.
    entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
    entityManager.refresh(user.getPerson());
    entityManager.refresh(user.getInstitution());
    final boolean sameAccount =
        user.getId().equals(authenticated.getId())
            && user.getInstitutionId().equals(attempt.institutionId());
    if (!sameAccount) {
      throw new InvalidLoginAttemptException();
    }
    if (!user.isEnabled() || !user.getPassword().equals(authenticatedPassword)) {
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
        authorityResolver.resolvePersonAuthorities(
            user.getPerson().getId(), user.getInstitutionId());
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
    // Public automatic access is not proof of credentials for sensitive account changes.
    if (!METHOD_AUTO_LOGIN.equals(method)) {
      recentAuthService.mark(session.sessionId(), user.getId(), method);
    }
    log.info(
        "[Auth] Login succeeded, userId: {}, institutionId: {}",
        user.getId(),
        user.getInstitutionId());

    return AuthResponse.of(
        user, user.getPerson().getId(), authorities, accessToken, session.refreshToken());
  }
}
