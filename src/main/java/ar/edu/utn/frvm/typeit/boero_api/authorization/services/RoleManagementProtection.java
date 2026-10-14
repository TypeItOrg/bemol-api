package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleManagementSelfLockoutException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RolePermissionRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class RoleManagementProtection {
  private static final Set<String> MANAGEMENT_PERMISSIONS =
      Set.of(
          PermissionCode.INSTITUTION_ROLE_READ.getCode(),
          PermissionCode.INSTITUTION_ROLE_UPDATE.getCode());
  private final PersonRoleAssignmentRepository assignments;
  private final RolePermissionRepository permissions;

  public Set<String> requiredForActor(
      final Role role, final UUID institutionId, final UUID actorPersonId) {
    if (!assignments.existsByPerson_IdAndRole_IdAndInstitution_Id(
        actorPersonId, role.getId(), institutionId)) {
      return Set.of();
    }

    // Role-management permissions are ineffective in training-path-limited assignments.
    final var otherRoleIds =
        assignments.findByPerson_IdAndInstitution_Id(actorPersonId, institutionId).stream()
            .filter(assignment -> assignment.getAccessScope() == AccessScope.INSTITUTION)
            .map(assignment -> assignment.getRole().getId())
            .filter(id -> !id.equals(role.getId()))
            .toList();
    final var required = new HashSet<>(MANAGEMENT_PERMISSIONS);
    if (!otherRoleIds.isEmpty()) {
      required.removeAll(permissions.findPermissionCodesByRoleIds(otherRoleIds));
    }

    return required;
  }

  public void requireRetained(
      final Role role,
      final UUID institutionId,
      final UUID actorPersonId,
      final Set<String> requested) {
    if (!InstitutionRolePermissions.expand(requested)
        .containsAll(requiredForActor(role, institutionId, actorPersonId))) {
      throw new RoleManagementSelfLockoutException();
    }
  }
}
