package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateShiftRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateShiftUseCase {
  private final AuditEventRecorder auditRecorder;
  private final ShiftRepository shiftRepository;
  private final InstitutionRepository institutionRepository;

  @Transactional
  public ShiftResponse execute(final UUID institutionId, final CreateShiftRequest request) {
    final var institution =
        institutionRepository
            .findById(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (shiftRepository.existsByNormalizedName(institutionId, name)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    try {
      final var saved =
          shiftRepository.save(Shift.create(institution, name, request.description()));
      shiftRepository.flush();
      auditRecorder.recordChange(
          saved.getInstitution(),
          null,
          AuditAction.SHIFT_CREATED,
          AuditEntityType.SHIFT,
          saved.getId(),
          saved.getName(),
          Map.of(),
          AcademicCatalogAuditDetails.snapshot(saved));
      return ShiftResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }
}
