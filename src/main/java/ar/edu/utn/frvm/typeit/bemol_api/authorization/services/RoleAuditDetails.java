package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RoleAuditDetails {
  private RoleAuditDetails() {}

  static List<Map<String, Object>> assignments(final List<PersonRoleAssignment> assignments) {
    return assignments.stream()
        .sorted(Comparator.comparing(assignment -> assignment.getRole().getId()))
        .map(
            assignment ->
                Map.<String, Object>of(
                    "roleId", assignment.getRole().getId().toString(),
                    "roleCode", assignment.getRole().getCode(),
                    "roleName", assignment.getRole().getName(),
                    "accessScope", assignment.getAccessScope().name(),
                    "trainingPathIds",
                        assignment.getTrainingPathIds().stream()
                            .map(id -> id.toString())
                            .sorted()
                            .toList()))
        .toList();
  }

  static Map<String, Object> definition(final InstitutionRoleResponse response) {
    return definition(response.name(), response.permissions());
  }

  static Map<String, Object> definition(final String name, final Set<String> codes) {
    return Map.of("name", name, "permissions", codes.stream().sorted().toList());
  }
}
