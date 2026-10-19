package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.StagingAutoLoginProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StagingAutoLoginUseCase {
  private final StagingAutoLoginProperties properties;
  private final Environment environment;
  private final UserRepository users;
  private final InstitutionalHostContext hostContext;
  private final AuthenticationSessionIssuer sessionIssuer;

  @Transactional
  public AuthResponse execute(final HttpServletRequest request) {
    final UUID userId = properties.userId();
    final UUID institutionId = properties.institutionId();
    if (!properties.enabled()
        || !environment.matchesProfiles(StagingAutoLoginProperties.PROFILE)
        || userId == null
        || institutionId == null) {
      throw new InvalidCredentialsException();
    }

    final var user =
        users
            .findWithPersonAndInstitutionById(userId)
            .orElseThrow(InvalidCredentialsException::new);
    if (!user.isEnabled() || !institutionId.equals(user.getInstitutionId())) {
      throw new InvalidCredentialsException();
    }

    hostContext.requireInstitution(user.getInstitutionId());

    return sessionIssuer.issue(
        user,
        AuthRequestMetadata.clientIp(request),
        request.getHeader("User-Agent"),
        false,
        AuthenticationSessionIssuer.METHOD_AUTO_LOGIN);
  }
}
