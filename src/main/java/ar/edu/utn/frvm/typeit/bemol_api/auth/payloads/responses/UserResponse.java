package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserResponse(UserPayload user) {

  public static UserResponse from(User user, UUID personId, Set<PermissionCode> permissions) {
    return UserResponse.builder().user(UserPayload.from(user, personId, permissions)).build();
  }

  public static UserResponse from(
      User user, UUID personId, Set<PermissionCode> permissions, List<String> roles) {
    return UserResponse.builder()
        .user(UserPayload.from(user, personId, permissions, roles))
        .build();
  }
}
