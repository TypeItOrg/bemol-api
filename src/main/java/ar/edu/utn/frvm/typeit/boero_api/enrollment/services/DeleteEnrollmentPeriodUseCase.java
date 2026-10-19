package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentPeriodNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentPeriodRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteEnrollmentPeriodUseCase {
  private final AuditEventRecorder auditRecorder;
  private final EnrollmentPeriodAccessService periodAccess;

  private final EnrollmentPeriodRepository periodRepository;
  private final EnrollmentInstitutionLock institutionLock;
  private final EnrollmentPeriodScopeService scopeService;
  private final Clock clock;

  @Transactional
  public void execute(final UUID institutionId, final UUID periodId) {
    institutionLock.lock(institutionId);

    final var period =
        periodRepository
            .findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId)
            .orElseThrow(EnrollmentPeriodNotFoundException::new);
    periodAccess.requireManage(period, PermissionCode.ENROLLMENT_PERIOD_DELETE);

    period.markDeleted(clock.instant());
    scopeService.save(period);
    auditRecorder.recordChange(
        period.getInstitution(),
        null,
        AuditAction.ENROLLMENT_PERIOD_DELETED,
        AuditEntityType.ENROLLMENT_PERIOD,
        period.getId(),
        period.getName(),
        Map.of("deleted", false),
        Map.of("deleted", true));
  }
}
