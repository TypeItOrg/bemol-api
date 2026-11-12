package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateShiftUseCase {
  private final AuditEventRecorder auditRecorder;
  private final ShiftRepository shiftRepository;

  @Transactional
  public ShiftResponse execute(
      final UUID institutionId, final UUID id, final UpdateShiftRequest request) {
    final var shift =
        shiftRepository
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(ShiftNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (shiftRepository.existsByNormalizedNameAndIdNot(institutionId, name, id)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    final var before = shift.auditSnapshot();
    shift.update(name, request.description());
    try {
      shiftRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    auditRecorder.recordSnapshotChange(
        shift.getInstitution(),
        null,
        AcademicAuditAction.SHIFT_UPDATED,
        Shift.AUDIT_TYPE,
        shift.getId(),
        shift.getName(),
        before,
        shift.auditSnapshot());
    return ShiftResponse.from(shift);
  }
}
