package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAcademicYearUseCase {

  private final AcademicYearRepository academicYearRepository;

  @Transactional(readOnly = true)
  public AcademicYearResponse execute(final UUID institutionId, final UUID id) {
    return academicYearRepository
        .findByIdAndInstitution_Id(id, institutionId)
        .map(AcademicYearResponse::from)
        .orElseThrow(AcademicYearNotFoundException::new);
  }
}
