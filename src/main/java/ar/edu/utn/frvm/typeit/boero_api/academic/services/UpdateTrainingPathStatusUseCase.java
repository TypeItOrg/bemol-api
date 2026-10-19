package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import java.util.Map;
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
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(TrainingPathNotFoundException::new);
    if (!request.active() && trainingPathRepository.existsActiveStudyPlan(id)) {
      throw new AcademicConflictException(AcademicMessages.TRAINING_PATH_HAS_ACTIVE_PLANS);
    }
    final var before = Map.<String, Object>of("active", path.isActive());
    path.updateStatus(request.active());
    trainingPathRepository.flush();
    auditRecorder.recordChange(
        path.getInstitution(),
        null,
        AuditAction.TRAINING_PATH_STATUS_CHANGED,
        AuditEntityType.TRAINING_PATH,
        path.getId(),
        path.getName(),
        before,
        Map.of("active", path.isActive()));
  }
}
