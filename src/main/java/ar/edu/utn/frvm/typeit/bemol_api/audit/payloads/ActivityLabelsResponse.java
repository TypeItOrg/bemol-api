package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Schema(requiredProperties = {"roleLabels", "permissionLabels"})
public record ActivityLabelsResponse(
    Map<String, String> roleLabels, Map<String, String> permissionLabels) {
  public static ActivityLabelsResponse catalog() {
    return new ActivityLabelsResponse(
        Arrays.stream(SystemRoleCode.values())
            .collect(Collectors.toMap(role -> role.name(), role -> role.getDisplayName())),
        Arrays.stream(PermissionCode.values())
            .collect(
                Collectors.toMap(
                    permission -> permission.getCode(),
                    permission -> permission.getDescription())));
  }
}
