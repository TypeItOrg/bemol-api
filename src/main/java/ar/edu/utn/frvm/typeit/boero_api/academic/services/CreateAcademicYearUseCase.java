package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicYearResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
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
public class CreateAcademicYearUseCase {
  private final AuditEventRecorder auditRecorder;

  private final AcademicYearRepository academicYearRepository;
  private final InstitutionRepository institutionRepository;
  private final BusinessDateProvider businessDateProvider;

  @Transactional
  public AcademicYearResponse execute(
      final UUID institutionId, final CreateAcademicYearRequest request) {
    final var institution =
        institutionRepository
            .findById(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    if (academicYearRepository.existsByInstitution_IdAndYearAndDeletedAtIsNull(
        institutionId, request.year())) {
      throw AcademicConflictException.forField("year", AcademicMessages.DUPLICATE_YEAR);
    }
    try {
      final var saved =
          academicYearRepository.save(
              AcademicYear.create(
                  institution,
                  request.year(),
                  request.startDate(),
                  request.endDate(),
                  businessDateProvider.today()));
      academicYearRepository.flush();
      auditRecorder.recordChange(
          saved.getInstitution(),
          null,
          AuditAction.ACADEMIC_YEAR_CREATED,
          AuditEntityType.ACADEMIC_YEAR,
          saved.getId(),
          String.valueOf(saved.getYear()),
          Map.of(),
          AcademicCatalogAuditDetails.snapshot(saved));
      return AcademicYearResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }
}
