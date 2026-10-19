package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import java.util.Map;
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
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(ShiftNotFoundException::new);
    final var before = Map.<String, Object>of("active", shift.isActive());
    shift.updateStatus(request.active());
    shiftRepository.flush();
    auditRecorder.recordChange(
        shift.getInstitution(),
        null,
        AuditAction.SHIFT_STATUS_CHANGED,
        AuditEntityType.SHIFT,
        shift.getId(),
        shift.getName(),
        before,
        Map.of("active", shift.isActive()));
  }
}
