package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("scopedAuthorization")
@RequiredArgsConstructor
public class ScopedAuthorizationService {
  private final AuthorityResolver authorityResolver;
  private final OperatingContextResolver operatingContext;
  private final CachedAuthoritySnapshotResolver snapshotResolver;

  public PermissionAccess access(PermissionCode permission) {
    if (operatingContext.currentPersonId().isPresent()) {
      return PermissionAccess.none();
    }
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)) {
      return PermissionAccess.none();
    }
    return authorityResolver
        .resolvePersonAuthorities(user.personId(), user.institutionId())
        .permissionScopes()
        .getOrDefault(permission, PermissionAccess.none());
  }

  public boolean isPlatformAdministrator() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getPrincipal() instanceof JwtAuthenticatedPlatformAccount platform
        && authorityResolver
            .resolvePlatformRoles(platform.platformAccountId())
            .contains(PlatformRoleCode.PLATFORM_ADMIN);
  }

  public PermissionAccess managementAccess(PermissionCode permission) {
    return isPlatformAdministrator() ? PermissionAccess.institution() : access(permission);
  }

  public PermissionAccess enrollmentOptionsAccess() {
    return managementAccess(PermissionCode.COURSE_ENROLLMENT_READ)
        .union(managementAccess(PermissionCode.COURSE_ENROLLMENT_CREATE))
        .union(managementAccess(PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL));
  }

  public boolean enrollmentOptionsUnrestricted() {
    return enrollmentOptionsAccess().institutional();
  }

  public Set<UUID> enrollmentOptionsPaths() {
    var paths = enrollmentOptionsAccess().trainingPathIds();
    return paths.isEmpty() ? Set.of(new UUID(0, 0)) : paths;
  }

  public PermissionAccess assignableTrainingPaths() {
    if (isPlatformAdministrator()) {
      return PermissionAccess.institution();
    }
    PermissionAccess result = PermissionAccess.none();
    for (var permission : PermissionCode.values()) {
      if (permission.supportsTrainingPaths()) {
        result = result.union(access(permission));
      }
    }
    return result;
  }

  public boolean unrestricted(String permission) {
    return managementAccess(PermissionCode.valueOf(permission)).institutional();
  }

  public Set<UUID> paths(String permission) {
    var paths = managementAccess(PermissionCode.valueOf(permission)).trainingPathIds();
    return paths.isEmpty() ? Set.of(new UUID(0, 0)) : paths;
  }

  public void require(PermissionCode permission, UUID institutionId, UUID trainingPathId) {
    if (isPlatformAdministrator()) {
      return;
    }
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)
        || !user.institutionId().equals(institutionId)
        || !access(permission).includes(trainingPathId)) {
      throw new ScopedResourceNotFoundException();
    }
  }

  public Set<PermissionCode> freshPermissions() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof JwtAuthenticatedUser user)) {
      return Set.of();
    }
    return snapshotResolver
        .resolveFreshForPerson(user.personId(), user.institutionId())
        .permissions();
  }

  public void requireDelegation(PermissionCode permission, PermissionAccess grant) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !(auth.getPrincipal() instanceof JwtAuthenticatedUser user)
        || !snapshotResolver
            .resolveFreshForPerson(user.personId(), user.institutionId())
            .permissionScopes()
            .getOrDefault(permission, PermissionAccess.none())
            .contains(grant)) {
      throw new PermissionDelegationNotAllowedException();
    }
  }
}
