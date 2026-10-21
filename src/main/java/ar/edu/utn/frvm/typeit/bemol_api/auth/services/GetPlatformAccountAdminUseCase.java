package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountAdminResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPlatformAccountAdminUseCase {

  private final PlatformAccountRepository platformAccountRepository;

  @Transactional(readOnly = true)
  public PlatformAccountAdminResponse execute(final UUID id) {
    return platformAccountRepository
        .findById(id)
        .map(PlatformAccountAdminResponse::from)
        .orElseThrow(PlatformAccountNotFoundException::new);
  }
}
