package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAuditSnapshotTest {
  @Test
  void permissionsHaveStableCodesAndDomainLabels() {
    final var permission = PermissionCode.values()[0];
    final var role = Role.builder().name("Operador").build();
    final var snapshot = role.auditSnapshot(Set.of("UNKNOWN_CODE", permission.getCode()));

    assertThat(snapshot.values().get("permissions"))
        .isEqualTo(List.of("UNKNOWN_CODE", permission.getCode()).stream().sorted().toList());
    assertThat(snapshot.visibleValues().toString())
        .contains(permission.getDescription(), "UNKNOWN_CODE");
    assertThat(
            role.auditSnapshot(new HashSet<>(List.of(permission.getCode(), "UNKNOWN_CODE")))
                .values())
        .isEqualTo(snapshot.values());
  }

  @Test
  void assignmentsPreserveReferencesAndDeclareOnlyPresentationFields() {
    final var role = Role.builder().id(UUID.randomUUID()).code("CUSTOM").name("Docente").build();
    final var path = UUID.randomUUID();
    final var assignment =
        PersonRoleAssignment.builder()
            .role(role)
            .accessScope(AccessScope.TRAINING_PATHS)
            .trainingPathIds(new HashSet<>(Set.of(path)))
            .build();
    final var before = PersonRoleAssignment.auditAssignments(List.of(assignment));

    assignment.changeAccessScope(AccessScope.TRAINING_PATHS, Set.of(UUID.randomUUID()));
    final var after = PersonRoleAssignment.auditAssignments(List.of(assignment));

    assertThat(before.values()).isNotEqualTo(after.values());
    assertThat(before.visibleValues().toString())
        .contains(role.getId().toString(), path.toString(), "Docente");
    assertThat(assignment.auditSnapshot().labels())
        .containsOnlyKeys("roleName", "accessScopeName", "trainingPathCount");
    assertThat(before.visibleValues().toString()).contains(path.toString());
  }

  @Test
  void assignmentOrderDoesNotChangeTheSnapshot() {
    final var first =
        PersonRoleAssignment.builder()
            .role(Role.builder().id(UUID.randomUUID()).code("A").name("A").build())
            .build();
    final var second =
        PersonRoleAssignment.builder()
            .role(Role.builder().id(UUID.randomUUID()).code("B").name("B").build())
            .build();

    assertThat(PersonRoleAssignment.auditAssignments(List.of(first, second)).values())
        .isEqualTo(PersonRoleAssignment.auditAssignments(List.of(second, first)).values());
  }
}
