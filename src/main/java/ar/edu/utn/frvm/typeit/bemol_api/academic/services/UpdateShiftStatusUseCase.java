package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateShiftStatusUseCase {
  private final AuditEventRecorder auditRecorder;
  private final ShiftRepository shiftRepository;

  @Transactional
  public void execute(final UUID institutionId, final UUID id, final ActiveStatusRequest request) {
    final var shift =
        shiftRepository
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(ShiftNotFoundException::new);
    final var before = shift.auditSnapshot();
    shift.updateStatus(request.active());
    shiftRepository.flush();
    auditRecorder.recordSnapshotChange(
        shift.getInstitution(),
        null,
        AcademicAuditAction.SHIFT_STATUS_CHANGED,
        Shift.AUDIT_TYPE,
        shift.getId(),
        shift.getName(),
        before,
        shift.auditSnapshot());
  }
}
