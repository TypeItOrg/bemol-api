package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import java.util.*;
import org.junit.jupiter.api.Test;

class RoleAssignmentScopeValidatorTest {
  private final TrainingPathRepository paths = mock(TrainingPathRepository.class);
  private final RolePermissionRepository permissions = mock(RolePermissionRepository.class);
  private final Role role = mock(Role.class);
  private final UUID institution = UUID.randomUUID();
  private final RoleAssignmentScopeValidator validator =
      new RoleAssignmentScopeValidator(paths, permissions, mock(ScopedAuthorizationService.class));

  @Test
  void rejectsEmptyInconsistentAndDuplicateSelections() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    institution,
                    role,
                    new AssignRoleRequest(UUID.randomUUID(), AccessScope.TRAINING_PATHS, Set.of()),
                    Set.of()))
        .isInstanceOf(InvalidAccessScopeException.class);
    UUID path = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                validator.validate(
                    institution,
                    role,
                    new AssignRoleRequest(UUID.randomUUID(), AccessScope.INSTITUTION, Set.of(path)),
                    Set.of()))
        .isInstanceOf(InvalidAccessScopeException.class);
    assertThatThrownBy(
            () ->
                new AssignRoleRequest(
                    UUID.randomUUID(), AccessScope.TRAINING_PATHS, List.of(path, path)))
        .isInstanceOf(InvalidAccessScopeException.class);
  }

  @Test
  void rejectsForeignPathsAndRolesWithoutApplicablePermissions() {
    UUID id = UUID.randomUUID();
    when(role.getId()).thenReturn(id);
    UUID path = UUID.randomUUID();
    var request = new AssignRoleRequest(id, AccessScope.TRAINING_PATHS, Set.of(path));
    assertThatThrownBy(() -> validator.validate(institution, role, request, Set.of()))
        .isInstanceOf(InvalidAccessScopeException.class);
    when(permissions.findPermissionCodesByRoleIds(List.of(id)))
        .thenReturn(List.of(PermissionCode.INSTITUTION_PERSON_READ_ANY.getCode()));
    assertThatThrownBy(() -> validator.validate(institution, role, request, Set.of(path)))
        .isInstanceOf(InvalidAccessScopeException.class);
  }

  @Test
  void preservesHistoricalSelectionWithoutRequiringAnActivePath() {
    UUID id = UUID.randomUUID(), historical = UUID.randomUUID();
    when(role.getId()).thenReturn(id);
    when(permissions.findPermissionCodesByRoleIds(List.of(id)))
        .thenReturn(List.of(PermissionCode.TRAINING_PATH_READ.getCode()));
    validator.validate(
        institution,
        role,
        new AssignRoleRequest(id, AccessScope.TRAINING_PATHS, Set.of(historical)),
        Set.of(historical));
    verifyNoInteractions(paths);
  }
}
