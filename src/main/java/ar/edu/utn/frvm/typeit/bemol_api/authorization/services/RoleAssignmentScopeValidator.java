package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleAssignmentScopeValidator {
  private final TrainingPathRepository trainingPathRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final ScopedAuthorizationService scopedAuthorization;

  public void validate(
      UUID institutionId, Role role, AssignRoleRequest request, Set<UUID> retained) {
    if (request.accessScope() == null
        || request.selectedTrainingPathIds() == null
        || (request.accessScope() == AccessScope.INSTITUTION
            && !request.selectedTrainingPathIds().isEmpty())
        || (request.accessScope() == AccessScope.TRAINING_PATHS
            && (request.selectedTrainingPathIds().isEmpty() || role.isInstitutionalAuthority()))) {
      throw new InvalidAccessScopeException();
    }
    for (UUID pathId : request.selectedTrainingPathIds()) {
      if (!retained.contains(pathId)
          && trainingPathRepository.findByIdAndInstitution_Id(pathId, institutionId).isEmpty()) {
        throw new InvalidAccessScopeException();
      }
    }
    if (request.accessScope() == AccessScope.TRAINING_PATHS
        && permissions(role).stream()
            .noneMatch(mappedPermissionCode -> mappedPermissionCode.supportsTrainingPaths())) {
      throw new InvalidAccessScopeException();
    }
  }

  public void requireDelegation(Role role, AccessScope scope, Set<UUID> paths) {
    PermissionAccess grant = new PermissionAccess(scope, paths);
    for (PermissionCode permission : permissions(role)) {
      if (scope == AccessScope.INSTITUTION || permission.supportsTrainingPaths()) {
        scopedAuthorization.requireDelegation(permission, grant);
      }
    }
  }

  public void requireOperation(PermissionCode permission) {
    scopedAuthorization.requireDelegation(permission, PermissionAccess.institution());
  }

  public Set<PermissionCode> freshPermissions() {
    return scopedAuthorization.freshPermissions();
  }

  public void requireDelegation(PersonRoleAssignment assignment) {
    requireDelegation(
        assignment.getRole(), assignment.getAccessScope(), assignment.getTrainingPathIds());
  }

  private Set<PermissionCode> permissions(Role role) {
    return rolePermissionRepository.findPermissionCodesByRoleIds(List.of(role.getId())).stream()
        .map(PermissionCode::fromCode)
        .collect(Collectors.toSet());
  }
}
