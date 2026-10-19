package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtPrincipal;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.OperatingContextResolver;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

  private final AuthorityResolver authorityResolver;
  private final OperatingContextResolver operatingContext;

  public boolean hasPermission(@Nullable Authentication authentication, PermissionCode permission) {
    return resolvePermissions(authentication).contains(permission);
  }

  public boolean hasAnyPermission(
      @Nullable Authentication authentication, PermissionCode... permissions) {
    Set<PermissionCode> granted = resolvePermissions(authentication);
    return Arrays.stream(permissions).anyMatch(granted::contains);
  }

  public boolean hasPlatformRole(@Nullable Authentication authentication, PlatformRoleCode role) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount platform)) {
      return false;
    }
    return authorityResolver.resolvePlatformRoles(platform.platformAccountId()).contains(role);
  }

  public Set<PermissionCode> resolvePermissions(@Nullable Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
      return Set.of();
    }
    return switch (principal) {
      case JwtAuthenticatedUser user -> resolveInstitutionalPermissions(user);
      case JwtAuthenticatedPlatformAccount platform -> resolvePlatformPermissions(platform);
    };
  }

  private Set<PermissionCode> resolveInstitutionalPermissions(JwtAuthenticatedUser principal) {
    final var selected = operatingContext.currentPersonId();
    return authorityResolver.resolveForPerson(
        selected.orElse(principal.personId()), principal.institutionId());
  }

  private Set<PermissionCode> resolvePlatformPermissions(
      JwtAuthenticatedPlatformAccount principal) {
    return authorityResolver.resolveForPlatformAccount(principal.platformAccountId());
  }
}
