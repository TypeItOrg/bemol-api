package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import java.util.Set;
import java.util.stream.Collectors;

@io.swagger.v3.oas.annotations.media.Schema(
    requiredProperties = {
      "code",
      "description",
      "grantable",
      "supportsTrainingPaths",
      "requiredPermissions"
    })
public record InstitutionPermissionResponse(
    String code,
    String description,
    boolean grantable,
    boolean supportsTrainingPaths,
    Set<String> requiredPermissions) {

  public static InstitutionPermissionResponse from(
      PermissionCode permission, Set<PermissionCode> actorPermissions) {
    return new InstitutionPermissionResponse(
        permission.getCode(),
        permission.getDescription(),
        actorPermissions.contains(permission),
        permission.supportsTrainingPaths(),
        permission.requiredPermissions().stream()
            .map(mappedPermissionCode -> mappedPermissionCode.getCode())
            .collect(Collectors.toUnmodifiableSet()));
  }
}
