package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentPeriodNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidEnrollmentPeriodDatesException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentPeriodRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateEnrollmentPeriodUseCase {
  private final AuditEventRecorder auditRecorder;
  private final EnrollmentPeriodAccessService periodAccess;

  private final EnrollmentPeriodRepository periodRepository;
  private final EnrollmentInstitutionLock institutionLock;
  private final EnrollmentPeriodScopeService scopeService;

  @Transactional
  public EnrollmentPeriodResponse execute(
      final UUID institutionId, final UUID periodId, final UpdateEnrollmentPeriodRequest request) {
    institutionLock.lock(institutionId);

    if (request.startDate().isAfter(request.endDate())) {
      throw new InvalidEnrollmentPeriodDatesException();
    }

    final var period =
        periodRepository
            .findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId)
            .orElseThrow(EnrollmentPeriodNotFoundException::new);

    periodAccess.requireManage(period, PermissionCode.ENROLLMENT_PERIOD_UPDATE);
    final var before = EnrollmentPeriodAuditDetails.snapshot(period);
    scopeService.configure(period, request.offerings());
    periodAccess.requireManage(period, PermissionCode.ENROLLMENT_PERIOD_UPDATE);
    period.updateDetails(request.name(), request.startDate(), request.endDate());
    final var saved = scopeService.save(period);

    auditRecorder.recordChange(
        period.getInstitution(),
        null,
        AuditAction.ENROLLMENT_PERIOD_UPDATED,
        AuditEntityType.ENROLLMENT_PERIOD,
        saved.getId(),
        saved.getName(),
        before,
        EnrollmentPeriodAuditDetails.snapshot(saved));
    return periodAccess.responseAfterMutation(saved, PermissionCode.ENROLLMENT_PERIOD_UPDATE);
  }
}
