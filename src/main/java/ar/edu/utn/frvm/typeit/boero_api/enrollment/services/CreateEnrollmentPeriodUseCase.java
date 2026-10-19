package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidEnrollmentPeriodDatesException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CreateEnrollmentPeriodRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentPeriodResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateEnrollmentPeriodUseCase {
  private final AuditEventRecorder auditRecorder;
  private final EnrollmentPeriodAccessService periodAccess;

  private final EnrollmentInstitutionLock institutionLock;
  private final EnrollmentPeriodScopeService scopeService;
  private final InstitutionRepository institutionRepository;
  private final AcademicYearRepository academicYearRepository;

  @Transactional
  public EnrollmentPeriodResponse execute(
      final UUID institutionId, final CreateEnrollmentPeriodRequest request) {
    institutionLock.lock(institutionId);

    if (request.startDate().isAfter(request.endDate())) {
      throw new InvalidEnrollmentPeriodDatesException();
    }

    final var institution =
        institutionRepository
            .findById(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);

    final var academicYear =
        academicYearRepository
            .findById(request.academicYearId())
            .filter(ay -> ay.getInstitution().getId().equals(institutionId))
            .filter(ay -> ay.getDeletedAt() == null)
            .orElseThrow(AcademicYearNotFoundException::new);

    final var period =
        EnrollmentPeriod.builder()
            .institution(institution)
            .academicYear(academicYear)
            .status(EnrollmentPeriodStatus.PLANNED)
            .build();
    period.updateDetails(request.name(), request.startDate(), request.endDate());

    scopeService.configure(period, request.offerings());
    periodAccess.requireManage(period, PermissionCode.ENROLLMENT_PERIOD_CREATE);
    final var saved = scopeService.save(period);

    auditRecorder.recordChange(
        institution,
        null,
        AuditAction.ENROLLMENT_PERIOD_CREATED,
        AuditEntityType.ENROLLMENT_PERIOD,
        saved.getId(),
        saved.getName(),
        Map.of(),
        EnrollmentPeriodAuditDetails.snapshot(saved));
    return periodAccess.responseAfterMutation(saved, PermissionCode.ENROLLMENT_PERIOD_CREATE);
  }
}
