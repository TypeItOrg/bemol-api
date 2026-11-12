package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateTrainingPathStatusUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final TrainingPathRepository trainingPathRepository;

  @Transactional
  public void execute(final UUID institutionId, final UUID id, final ActiveStatusRequest request) {
    accessGuard.require(
        PermissionCode.TRAINING_PATH_STATUS_UPDATE,
        institutionId,
        ScopedResource.TRAINING_PATH,
        id);

    final var path =
        trainingPathRepository
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(TrainingPathNotFoundException::new);
    if (!request.active() && trainingPathRepository.existsActiveStudyPlan(id)) {
      throw new AcademicConflictException(AcademicMessages.TRAINING_PATH_HAS_ACTIVE_PLANS);
    }
    final var before = path.auditSnapshot();
    path.updateStatus(request.active());
    trainingPathRepository.flush();
    auditRecorder.recordSnapshotChange(
        path.getInstitution(),
        null,
        AcademicAuditAction.TRAINING_PATH_STATUS_CHANGED,
        TrainingPath.AUDIT_TYPE,
        path.getId(),
        path.getName(),
        before,
        path.auditSnapshot());
  }
}
