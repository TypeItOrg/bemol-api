package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateTrainingPathUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final TrainingPathRepository trainingPathRepository;

  @Transactional
  public TrainingPathResponse execute(
      final UUID institutionId, final UUID id, final UpdateTrainingPathRequest request) {
    accessGuard.require(
        PermissionCode.TRAINING_PATH_UPDATE, institutionId, ScopedResource.TRAINING_PATH, id);

    final var path =
        trainingPathRepository
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(TrainingPathNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (trainingPathRepository.existsByNormalizedNameAndIdNot(institutionId, name, id)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    final var before = AcademicCatalogAuditDetails.snapshot(path);
    path.update(name, request.description());
    try {
      trainingPathRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    auditRecorder.recordChange(
        path.getInstitution(),
        null,
        AuditAction.TRAINING_PATH_UPDATED,
        AuditEntityType.TRAINING_PATH,
        path.getId(),
        path.getName(),
        before,
        AcademicCatalogAuditDetails.snapshot(path));
    return TrainingPathResponse.from(path);
  }
}
