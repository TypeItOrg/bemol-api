package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InstrumentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.InstrumentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInstrumentUseCase {
  private final AuditEventRecorder auditRecorder;
  private final InstrumentRepository instrumentRepository;

  @Transactional
  public InstrumentResponse execute(
      final UUID institutionId, final UUID id, final UpdateInstrumentRequest request) {
    final var instrument =
        instrumentRepository
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(InstrumentNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (instrumentRepository.existsByNormalizedNameAndIdNot(institutionId, name, id)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    final var before = AcademicCatalogAuditDetails.snapshot(instrument);
    instrument.update(name, request.description());
    try {
      instrumentRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    auditRecorder.recordChange(
        instrument.getInstitution(),
        null,
        AuditAction.INSTRUMENT_UPDATED,
        AuditEntityType.INSTRUMENT,
        instrument.getId(),
        instrument.getName(),
        before,
        AcademicCatalogAuditDetails.snapshot(instrument));
    return InstrumentResponse.from(instrument);
  }
}
