package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import lombok.Builder;

@Builder
public record PlatformAccountResponse(PlatformAccountPayload account) {

  public static PlatformAccountResponse from(PlatformAccount account) {
    return PlatformAccountResponse.builder().account(PlatformAccountPayload.from(account)).build();
  }
}
