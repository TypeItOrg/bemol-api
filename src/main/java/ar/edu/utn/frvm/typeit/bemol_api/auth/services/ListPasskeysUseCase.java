package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPasskeysUseCase {

  private final PasskeyCredentialRepository passkeyCredentialRepository;

  @Transactional(readOnly = true)
  public List<PasskeyResponse> execute(final JwtAuthenticatedUser principal) {
    if (principal.delegated() || principal.accountUserId().isEmpty()) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException();
    }

    return passkeyCredentialRepository.findActiveByUserId(principal.userId()).stream()
        .map(PasskeyResponse::from)
        .toList();
  }
}
