package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionGroup;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public record InstitutionPermissionGroupResponse(
    String code,
    String displayName,
    String description,
    List<InstitutionPermissionResponse> permissions) {

  public static InstitutionPermissionGroupResponse from(
      PermissionGroup group, Set<PermissionCode> actorPermissions) {
    List<InstitutionPermissionResponse> permissions =
        Arrays.stream(PermissionCode.values())
            .filter(permission -> permission.getGroup() == group)
            .filter(mappedPermissionCode -> mappedPermissionCode.isConfigurable())
            .map(permission -> InstitutionPermissionResponse.from(permission, actorPermissions))
            .toList();

    return new InstitutionPermissionGroupResponse(
        group.name(), group.getDisplayName(), group.getDescription(), permissions);
  }
}
