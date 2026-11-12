package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
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
    CurrentPrincipal.requireInstitutionalUser(authentication);
  }

  public void ensureCallerBelongsToInstitution(
      @Nullable Authentication authentication, UUID institutionId) {
    if (isPlatformAdministrator(authentication)) {
      return;
    }

    final var user = CurrentPrincipal.requireInstitutionalUser(authentication);
    if (!user.institutionId().equals(institutionId)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  private boolean isPlatformAdministrator(final @Nullable Authentication authentication) {
    final boolean platformCaller = CurrentPrincipal.platformAccount(authentication).isPresent();

    return platformCaller
        && authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN);
  }
}
