package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
    return CurrentPrincipal.institutionalUser()
        .map(
            user ->
                authorityResolver.resolvePersonAuthorities(user.personId(), user.institutionId()))
        .map(authorities -> authorities.permissionScopes())
        .map(scopes -> scopes.getOrDefault(permission, PermissionAccess.none()))
        .orElse(PermissionAccess.none());
  }

  public boolean isPlatformAdministrator() {
    return CurrentPrincipal.platformAccount()
        .map(platform -> authorityResolver.resolvePlatformRoles(platform.platformAccountId()))
        .map(roles -> roles.contains(PlatformRoleCode.PLATFORM_ADMIN))
        .orElse(false);
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
    final boolean sameInstitution =
        CurrentPrincipal.institutionalUser()
            .map(user -> user.institutionId().equals(institutionId))
            .orElse(false);

    if (!sameInstitution || !access(permission).includes(trainingPathId)) {
      throw new ScopedResourceNotFoundException();
    }
  }

  public Set<PermissionCode> freshPermissions() {
    return CurrentPrincipal.institutionalUser()
        .map(user -> snapshotResolver.resolveFreshForPerson(user.personId(), user.institutionId()))
        .map(snapshot -> snapshot.permissions())
        .orElse(Set.of());
  }

  public void requireDelegation(PermissionCode permission, PermissionAccess grant) {
    final boolean canDelegate =
        CurrentPrincipal.institutionalUser()
            .map(
                user ->
                    snapshotResolver.resolveFreshForPerson(user.personId(), user.institutionId()))
            .map(snapshot -> snapshot.permissionScopes())
            .map(scopes -> scopes.getOrDefault(permission, PermissionAccess.none()))
            .map(held -> held.contains(grant))
            .orElse(false);

    if (!canDelegate) {
      throw new PermissionDelegationNotAllowedException();
    }
  }
}
