package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentPeriodNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentPeriodStatusRequest;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateEnrollmentPeriodStatusUseCase {
  private final AuditEventRecorder auditRecorder;
  private final EnrollmentPeriodAccessService periodAccess;

  private final EnrollmentPeriodRepository periodRepository;
  private final EnrollmentInstitutionLock institutionLock;
  private final EnrollmentPeriodScopeService scopeService;

  @Transactional
  public void execute(
      final UUID institutionId, final UUID periodId, final EnrollmentPeriodStatusRequest request) {
    institutionLock.lock(institutionId);

    final var period =
        periodRepository
            .findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId)
            .orElseThrow(EnrollmentPeriodNotFoundException::new);
    periodAccess.requireManage(period, PermissionCode.ENROLLMENT_PERIOD_STATUS_UPDATE);

    if (request.status() == EnrollmentPeriodStatus.OPEN && !period.isScopeConfigured()) {
      throw new ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions
          .EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_REQUIRED);
    }
    final var before = Map.<String, Object>of("status", period.getStatus().name());
    period.changeStatus(request.status());
    scopeService.save(period);
    auditRecorder.recordChange(
        period.getInstitution(),
        null,
        AuditAction.ENROLLMENT_PERIOD_STATUS_CHANGED,
        AuditEntityType.ENROLLMENT_PERIOD,
        period.getId(),
        period.getName(),
        before,
        Map.of("status", period.getStatus().name()));
  }
}
