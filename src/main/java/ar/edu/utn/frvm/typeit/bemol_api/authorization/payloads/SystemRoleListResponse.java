package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import java.util.List;
import lombok.Builder;

@Builder
public record SystemRoleListResponse(List<SystemRoleResponse> roles) {

  public static SystemRoleListResponse from(List<SystemRoleResponse> roles) {
    return SystemRoleListResponse.builder().roles(roles).build();
  }
}
