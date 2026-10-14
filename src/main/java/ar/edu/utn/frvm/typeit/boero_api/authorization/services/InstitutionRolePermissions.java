package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.RolePermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RolePermissionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstitutionRolePermissions {
  private final PermissionRepository permissions;
  private final RolePermissionRepository rolePermissions;
  private final PersonRoleAssignmentRepository assignments;
  private final ScopedAuthorizationService authorization;

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public Set<String> codes(final Role role) {
    return rolePermissions.findByRole_Id(role.getId()).stream()
        .map(value -> value.getPermission().getCode())
        .collect(Collectors.toUnmodifiableSet());
  }

  public static Set<String> expand(final Set<String> codes) {
    final var requested = codes.stream().map(PermissionCode::fromCode).collect(Collectors.toSet());
    return PermissionCode.withRequiredPermissions(requested).stream()
        .map(permission -> permission.getCode())
        .collect(Collectors.toUnmodifiableSet());
  }

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public void requireDelegableChanges(final Role role, final Set<String> requested) {
    final var desired = expand(requested);
    final var existing = codes(role);
    final var changed = new HashSet<>(desired);
    changed.addAll(existing);
    changed.removeIf(code -> desired.contains(code) == existing.contains(code));

    for (final var assignment : assignments.findByRole_Id(role.getId())) {
      for (final var code : changed) {
        final var permission = PermissionCode.fromCode(code);
        if (assignment.getAccessScope() == AccessScope.INSTITUTION
            || permission.supportsTrainingPaths()) {
          authorization.requireDelegation(
              permission,
              new PermissionAccess(assignment.getAccessScope(), assignment.getTrainingPathIds()));
        }
      }
      if (assignment.getAccessScope() == AccessScope.TRAINING_PATHS
          && desired.stream()
              .map(PermissionCode::fromCode)
              .noneMatch(permission -> permission.supportsTrainingPaths())) {
        throw new InvalidAccessScopeException();
      }
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void replace(
      final Role role,
      final Set<String> requested,
      final Set<PermissionCode> actorPermissions,
      final boolean preserveUnmanageable) {
    final var expanded = expand(requested);
    if (expanded.stream()
            .map(PermissionCode::fromCode)
            .noneMatch(permission -> permission.supportsTrainingPaths())
        && assignments.findByRole_Id(role.getId()).stream()
            .anyMatch(value -> value.getAccessScope() == AccessScope.TRAINING_PATHS)) {
      throw new InvalidAccessScopeException();
    }
    final var grantable =
        actorPermissions.stream()
            .filter(permission -> permission.getScope() == PermissionScope.INSTITUTION)
            .filter(permission -> permission.isConfigurable())
            .map(permission -> permission.getCode())
            .collect(Collectors.toSet());
    if (!grantable.containsAll(expanded)) {
      throw new PermissionDelegationNotAllowedException();
    }

    final var existing = rolePermissions.findByRole_Id(role.getId());
    final var existingCodes =
        existing.stream().map(value -> value.getPermission().getCode()).collect(Collectors.toSet());
    final Set<String> desired =
        preserveUnmanageable
            ? existingCodes.stream()
                .filter(code -> !grantable.contains(code))
                .collect(Collectors.toSet())
            : new HashSet<>();
    desired.addAll(expanded);

    existing.stream()
        .filter(value -> !desired.contains(value.getPermission().getCode()))
        .forEach(rolePermissions::delete);
    for (final var permission : permissions.findByCodeIn(List.copyOf(desired))) {
      if (!rolePermissions.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
        rolePermissions.save(RolePermission.of(role, permission));
      }
    }
  }
}
