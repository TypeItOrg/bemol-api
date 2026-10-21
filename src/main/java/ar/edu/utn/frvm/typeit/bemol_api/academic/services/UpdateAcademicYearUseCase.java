package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentInstitutionLock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateAcademicYearUseCase {
  private final AuditEventRecorder auditRecorder;

  private final AcademicYearRepository academicYearRepository;
  private final BusinessDateProvider businessDateProvider;
  private final EnrollmentInstitutionLock institutionLock;
  private final UpdateAcademicYearStatusUseCase statusChanges;

  @Transactional
  public AcademicYearResponse execute(
      final UUID institutionId, final UUID id, final UpdateAcademicYearRequest request) {
    institutionLock.lock(institutionId);
    final var academicYear =
        academicYearRepository
            .findByIdAndInstitution_IdForUpdate(id, institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    if (academicYearRepository.existsByInstitution_IdAndYearAndIdNotAndDeletedAtIsNull(
        institutionId, request.year(), id)) {
      throw AcademicConflictException.forField("year", AcademicMessages.DUPLICATE_YEAR);
    }

    final var before = AcademicCatalogAuditDetails.snapshot(academicYear);
    academicYear.update(
        request.year(), request.startDate(), request.endDate(), businessDateProvider.today());
    auditRecorder.recordChange(
        academicYear.getInstitution(),
        null,
        AuditAction.ACADEMIC_YEAR_UPDATED,
        AuditEntityType.ACADEMIC_YEAR,
        academicYear.getId(),
        String.valueOf(academicYear.getYear()),
        before,
        AcademicCatalogAuditDetails.snapshot(academicYear));
    if (request.status() != null && request.status() != academicYear.getStatus()) {
      statusChanges.change(institutionId, academicYear, request.status());
    }

    try {
      academicYearRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }

    return AcademicYearResponse.from(academicYear);
  }
}
