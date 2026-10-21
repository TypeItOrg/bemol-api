package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import java.util.Map;
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
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(AcademicSpaceNotFoundException::new);
    if (!request.active() && academicSpaceRepository.existsInEditableOrActivePlan(id)) {
      throw new AcademicConflictException(
          AcademicMessages.ACADEMIC_SPACE_HAS_ACTIVE_OR_DRAFT_PLANS);
    }
    final var before = Map.<String, Object>of("active", space.isActive());
    space.updateStatus(request.active());
    academicSpaceRepository.flush();
    auditRecorder.recordChange(
        space.getInstitution(),
        null,
        AuditAction.ACADEMIC_SPACE_STATUS_CHANGED,
        AuditEntityType.ACADEMIC_SPACE,
        space.getId(),
        space.getName(),
        before,
        Map.of("active", space.isActive()));
  }
}
