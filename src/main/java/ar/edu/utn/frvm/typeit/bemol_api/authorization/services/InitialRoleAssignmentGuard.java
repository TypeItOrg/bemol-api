package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialRoleAssignmentGuard {

  private final AuthorizationService authorizationService;
  private final InstitutionalSystemRoleResolver systemRoleResolver;
  private final RoleAssignmentScopeValidator scopeValidator;

  public void check(
      final @Nullable Authentication authentication, final @Nullable SystemRoleCode initialRole) {
    if (initialRole == null || initialRole == SystemRoleCode.APPLICANT) {
      return;
    }

    if (isPlatformAdministrator(authentication)) {
      return;
    }

    final boolean canAssignRoles =
        authorizationService.hasActorPermission(
            authentication, PermissionCode.INSTITUTION_ROLE_ASSIGN);
    if (!canAssignRoles) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    if (initialRole == SystemRoleCode.INSTITUTIONAL_AUTHORITY) {
      throw new RoleNotAssignableException();
    }
  }

  public void checkDelegation(
      final @Nullable Authentication authentication,
      final UUID institutionId,
      final SystemRoleCode initialRole) {
    if (initialRole == SystemRoleCode.APPLICANT || isPlatformAdministrator(authentication)) {
      return;
    }
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)
        || !user.institutionId().equals(institutionId)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    if (initialRole == SystemRoleCode.INSTITUTIONAL_AUTHORITY) {
      throw new RoleNotAssignableException();
    }

    scopeValidator.requireOperation(PermissionCode.INSTITUTION_ROLE_ASSIGN);
    final var role = systemRoleResolver.requireInstitutionalSystemRole(institutionId, initialRole);
    scopeValidator.requireDelegation(role, AccessScope.INSTITUTION, Set.of());
  }

  private boolean isPlatformAdministrator(final @Nullable Authentication authentication) {
    return authentication != null
        && authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount
        && authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN);
  }
}
