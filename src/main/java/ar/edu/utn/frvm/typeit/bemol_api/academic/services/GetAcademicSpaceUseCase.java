package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAcademicSpaceUseCase {
  private final AcademicSpaceRepository academicSpaceRepository;

  @Transactional(readOnly = true)
  public AcademicSpaceResponse execute(final UUID institutionId, final UUID id) {
    return academicSpaceRepository
        .findByIdAndInstitution_Id(id, institutionId)
        .map(AcademicSpaceResponse::from)
        .orElseThrow(AcademicSpaceNotFoundException::new);
  }
}
