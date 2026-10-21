package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionGroup;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionScope;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;

@Builder
@io.swagger.v3.oas.annotations.media.Schema(
    requiredProperties = {
      "code",
      "description",
      "scope",
      "group",
      "groupDisplayName",
      "groupDescription",
      "configurable",
      "supportsTrainingPaths",
      "requiredPermissions"
    })
public record PermissionCatalogItemResponse(
    String code,
    String description,
    PermissionScope scope,
    PermissionGroup group,
    String groupDisplayName,
    String groupDescription,
    boolean configurable,
    boolean supportsTrainingPaths,
    Set<String> requiredPermissions) {

  public static PermissionCatalogItemResponse from(PermissionCode permission) {
    return PermissionCatalogItemResponse.builder()
        .code(permission.getCode())
        .description(permission.getDescription())
        .scope(permission.getScope())
        .group(permission.getGroup())
        .groupDisplayName(permission.getGroup().getDisplayName())
        .groupDescription(permission.getGroup().getDescription())
        .configurable(permission.isConfigurable())
        .supportsTrainingPaths(permission.supportsTrainingPaths())
        .requiredPermissions(
            permission.requiredPermissions().stream()
                .map(mappedPermissionCode -> mappedPermissionCode.getCode())
                .collect(Collectors.toUnmodifiableSet()))
        .build();
  }
}
