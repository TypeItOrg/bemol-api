package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleAdministrationLock {
  private final InstitutionRepository institutions;

  @Transactional
  public void lock(UUID institutionId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
  }
}
