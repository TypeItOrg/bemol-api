package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCurrentPlatformAccountUseCase {

  private final PlatformAccountRepository platformAccountRepository;

  public PlatformAccountResponse execute(JwtAuthenticatedPlatformAccount principal) {
    var account =
        platformAccountRepository
            .findById(principal.platformAccountId())
            .orElseThrow(InvalidCredentialsException::new);
    if (!account.isEnabled()) {
      throw new DisabledException(AuthMessages.PLATFORM_ACCOUNT_DISABLED);
    }
    return PlatformAccountResponse.from(account);
  }
}
