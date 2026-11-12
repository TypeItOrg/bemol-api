package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
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
      auditRecorder.recordSnapshotChange(
          saved.getInstitution(),
          null,
          AcademicAuditAction.ACADEMIC_YEAR_CREATED,
          AcademicYear.AUDIT_TYPE,
          saved.getId(),
          String.valueOf(saved.getYear()),
          AuditSnapshot.empty(),
          saved.auditSnapshot());
      return AcademicYearResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }
}
