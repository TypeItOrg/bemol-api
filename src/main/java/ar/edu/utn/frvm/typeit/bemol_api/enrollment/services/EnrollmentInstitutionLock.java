package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EnrollmentInstitutionLock {
  private final InstitutionRepository institutionRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(final UUID institutionId) {
    institutionRepository
        .findByIdForUpdate(institutionId)
        .orElseThrow(EnrollmentApplicationNotFoundException::new);
  }
}
