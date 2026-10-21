package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@io.swagger.v3.oas.annotations.media.Schema(
    requiredProperties = {
      "id",
      "name",
      "technicalCode",
      "editable",
      "deletable",
      "assignmentCount",
      "permissions",
      "protectedPermissions",
      "supportsTrainingPathScope",
      "inactivePermissionDescriptionsWhenScoped"
    })
public record InstitutionRoleResponse(
    UUID id,
    String name,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
        @Nullable SystemRoleCode technicalCode,
    boolean editable,
    boolean deletable,
    long assignmentCount,
    Set<String> permissions,
    Set<String> protectedPermissions,
    boolean supportsTrainingPathScope,
    List<String> inactivePermissionDescriptionsWhenScoped) {

  public static InstitutionRoleResponse from(
      Role role, long assignmentCount, Set<String> permissions) {
    return from(role, assignmentCount, permissions, Set.of());
  }

  public static InstitutionRoleResponse from(
      Role role, long assignmentCount, Set<String> permissions, Set<String> protectedPermissions) {
    @Nullable SystemRoleCode technicalCode =
        role.isSystem() ? SystemRoleCode.valueOf(role.getCode()) : null;
    boolean authority = technicalCode == SystemRoleCode.INSTITUTIONAL_AUTHORITY;
    return InstitutionRoleResponse.builder()
        .id(role.getId())
        .name(role.getName())
        .technicalCode(technicalCode)
        .editable(!authority)
        .deletable(!role.isSystem() && assignmentCount == 0)
        .assignmentCount(assignmentCount)
        .supportsTrainingPathScope(
            permissions.stream()
                .map(PermissionCode::fromCode)
                .anyMatch(mappedPermissionCode -> mappedPermissionCode.supportsTrainingPaths()))
        .inactivePermissionDescriptionsWhenScoped(
            permissions.stream()
                .map(PermissionCode::fromCode)
                .filter(permission -> !permission.supportsTrainingPaths())
                .map(mappedPermissionCode -> mappedPermissionCode.getDescription())
                .sorted()
                .toList())
        .permissions(permissions)
        .protectedPermissions(protectedPermissions)
        .build();
  }
}
