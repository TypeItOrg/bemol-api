package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionRoleResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class InstitutionRoleResponseFactory {
  private final PersonRoleAssignmentRepository assignments;
  private final RolePermissionRepository permissions;

  public InstitutionRoleResponse from(final Role role) {
    final var codes =
        permissions.findByRole_Id(role.getId()).stream()
            .map(value -> value.getPermission().getCode())
            .collect(Collectors.toUnmodifiableSet());

    return from(role, codes, Set.of());
  }

  public InstitutionRoleResponse from(
      final Role role, final Set<String> codes, final Set<String> protectedCodes) {
    return InstitutionRoleResponse.from(
        role, assignments.countByRole_Id(role.getId()), codes, protectedCodes);
  }

  public List<InstitutionRoleResponse> from(final List<Role> roles) {
    if (roles.isEmpty()) {
      return List.of();
    }

    final var ids = roles.stream().map(role -> role.getId()).toList();
    final var counts =
        assignments.countByRoleIds(ids).stream()
            .collect(Collectors.toMap(row -> row.getRoleId(), row -> row.getAssignmentCount()));
    final var codes =
        permissions.findByRole_IdIn(ids).stream()
            .collect(
                Collectors.groupingBy(
                    value -> value.getRole().getId(),
                    Collectors.mapping(
                        value -> value.getPermission().getCode(), Collectors.toUnmodifiableSet())));

    return roles.stream()
        .map(
            role ->
                InstitutionRoleResponse.from(
                    role,
                    counts.getOrDefault(role.getId(), 0L),
                    codes.getOrDefault(role.getId(), Set.of()),
                    Set.of()))
        .toList();
  }
}
