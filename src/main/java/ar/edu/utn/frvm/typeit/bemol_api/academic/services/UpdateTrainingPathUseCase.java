package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
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
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(TrainingPathNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (trainingPathRepository.existsByNormalizedNameAndIdNot(institutionId, name, id)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    final var before = path.auditSnapshot();
    path.update(name, request.description());
    try {
      trainingPathRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    auditRecorder.recordSnapshotChange(
        path.getInstitution(),
        null,
        AcademicAuditAction.TRAINING_PATH_UPDATED,
        TrainingPath.AUDIT_TYPE,
        path.getId(),
        path.getName(),
        before,
        path.auditSnapshot());
    return TrainingPathResponse.from(path);
  }
}
