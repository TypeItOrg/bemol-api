package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateAcademicSpaceStatusUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicSpaceRepository academicSpaceRepository;

  @Transactional
  public void execute(final UUID institutionId, final UUID id, final ActiveStatusRequest request) {
    final var space =
        academicSpaceRepository
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(AcademicSpaceNotFoundException::new);
    if (!request.active() && academicSpaceRepository.existsInEditableOrActivePlan(id)) {
      throw new AcademicConflictException(
          AcademicMessages.ACADEMIC_SPACE_HAS_ACTIVE_OR_DRAFT_PLANS);
    }
    final var before = space.auditSnapshot();
    space.updateStatus(request.active());
    academicSpaceRepository.flush();
    auditRecorder.recordSnapshotChange(
        space.getInstitution(),
        null,
        AcademicAuditAction.ACADEMIC_SPACE_STATUS_CHANGED,
        AcademicSpace.AUDIT_TYPE,
        space.getId(),
        space.getName(),
        before,
        space.auditSnapshot());
  }
}
