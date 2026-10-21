package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetTrainingPathUseCase {
  private final AcademicAccessGuard accessGuard;
  private final TrainingPathRepository trainingPathRepository;

  @Transactional(readOnly = true)
  public TrainingPathResponse execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.TRAINING_PATH_READ, institutionId, ScopedResource.TRAINING_PATH, id);

    return trainingPathRepository
        .findByIdAndInstitution_Id(id, institutionId)
        .map(TrainingPathResponse::from)
        .orElseThrow(TrainingPathNotFoundException::new);
  }
}
