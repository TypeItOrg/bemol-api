package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstitutionalCallerGuard {

  private final AuthorizationService authorizationService;

  public void ensureInstitutionalPrincipal(@Nullable Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  public void ensureCallerBelongsToInstitution(
      @Nullable Authentication authentication, UUID institutionId) {
    if (authentication == null || authentication.getPrincipal() == null) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }

    if (authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount
        && authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN)) {
      return;
    }

    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }

    if (!user.institutionId().equals(institutionId)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }
}
