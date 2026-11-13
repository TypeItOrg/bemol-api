package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InstrumentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.InstrumentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
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
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(InstrumentNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (instrumentRepository.existsByNormalizedNameAndIdNot(institutionId, name, id)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    final var before = instrument.auditSnapshot();
    instrument.update(name, request.description(), request.icon());
    try {
      instrumentRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    auditRecorder.recordSnapshotChange(
        instrument.getInstitution(),
        null,
        AcademicAuditAction.INSTRUMENT_UPDATED,
        Instrument.AUDIT_TYPE,
        instrument.getId(),
        instrument.getName(),
        before,
        instrument.auditSnapshot());
    return InstrumentResponse.from(instrument);
  }
}
