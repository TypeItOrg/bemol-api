package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PlatformAccountPayload(
    UUID platformAccountId, String email, String name, String lastName) {

  public static PlatformAccountPayload from(PlatformAccount account) {
    return PlatformAccountPayload.builder()
        .platformAccountId(account.getId())
        .email(account.getEmail())
        .name(account.getName())
        .lastName(account.getLastName())
        .build();
  }
}
