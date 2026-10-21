package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AuthResponse(UserPayload user, TokenResponse tokens) {

  public static AuthResponse of(
      User user,
      UUID personId,
      Set<PermissionCode> permissions,
      String accessToken,
      String refreshToken) {
    return AuthResponse.builder()
        .user(UserPayload.from(user, personId, permissions))
        .tokens(TokenResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build())
        .build();
  }
}
