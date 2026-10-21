package ar.edu.utn.frvm.typeit.bemol_api.auth.filters;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccountType;
import java.security.Principal;
import java.util.UUID;

public sealed interface JwtPrincipal extends Principal
    permits JwtAuthenticatedUser, JwtAuthenticatedPlatformAccount {

  AccountType accountType();

  UUID sessionId();

  String tokenId();
}
