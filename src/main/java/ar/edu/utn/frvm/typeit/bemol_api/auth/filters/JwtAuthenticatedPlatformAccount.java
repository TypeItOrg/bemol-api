package ar.edu.utn.frvm.typeit.bemol_api.auth.filters;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccountType;
import java.util.UUID;
import lombok.Builder;

@Builder
public record JwtAuthenticatedPlatformAccount(
    UUID platformAccountId, String email, UUID sessionId, String tokenId) implements JwtPrincipal {

  @Override
  public AccountType accountType() {
    return AccountType.PLATFORM;
  }

  @Override
  public String getName() {
    return email;
  }
}
