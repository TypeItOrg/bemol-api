package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import lombok.Builder;

@Builder
public record SystemRoleResponse(SystemRoleCode code, String displayName) {

  public static SystemRoleResponse from(Role role) {
    return SystemRoleResponse.builder()
        .code(SystemRoleCode.valueOf(role.getCode()))
        .displayName(role.getName())
        .build();
  }
}
