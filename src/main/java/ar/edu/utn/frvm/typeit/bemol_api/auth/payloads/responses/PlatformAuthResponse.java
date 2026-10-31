package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public record PlatformAuthResponse(PlatformAccountPayload account, TokenResponse tokens)
    implements PlatformLoginResponse {

  public static PlatformAuthResponse of(
      PlatformAccount account, String accessToken, String refreshToken) {
    return PlatformAuthResponse.builder()
        .account(PlatformAccountPayload.from(account))
        .tokens(TokenResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build())
        .build();
  }
}
