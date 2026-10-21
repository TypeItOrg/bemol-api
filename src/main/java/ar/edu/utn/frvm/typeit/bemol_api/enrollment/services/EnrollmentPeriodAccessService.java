package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodOfferingResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnrollmentPeriodAccessService {
  private final ScopedAuthorizationService authorization;

  public Set<UUID> paths(EnrollmentPeriod period) {
    return period.getOfferings().stream()
        .map(o -> o.getStudyPlan().getTrainingPath().getId())
        .collect(Collectors.toSet());
  }

  public boolean canManage(EnrollmentPeriod period, PermissionCode permission) {
    var access = authorization.managementAccess(permission);
    var paths = paths(period);
    return access.institutional()
        || (!paths.isEmpty() && paths.stream().allMatch(access::includes));
  }

  public void requireManage(EnrollmentPeriod period, PermissionCode permission) {
    if (!canManage(period, permission)) {
      throw new ScopedResourceNotFoundException();
    }
  }

  public EnrollmentPeriodResponse response(EnrollmentPeriod period) {
    return response(period, authorization.managementAccess(PermissionCode.ENROLLMENT_PERIOD_READ));
  }

  public EnrollmentPeriodResponse responseAfterMutation(
      EnrollmentPeriod period, PermissionCode operation) {
    requireManage(period, operation);
    return response(period, authorization.managementAccess(operation));
  }

  private EnrollmentPeriodResponse response(EnrollmentPeriod period, PermissionAccess access) {
    var visible =
        period.getOfferings().stream()
            .filter(o -> access.includes(o.getStudyPlan().getTrainingPath().getId()))
            .map(EnrollmentPeriodOfferingResponse::from)
            .toList();
    if (!access.institutional() && visible.isEmpty()) {
      throw new ScopedResourceNotFoundException();
    }
    return new EnrollmentPeriodResponse(
        period.getId(),
        period.getInstitution().getId(),
        period.getAcademicYear().getId(),
        period.getAcademicYear().getYear(),
        period.getName(),
        period.getStartDate(),
        period.getEndDate(),
        period.getStatus(),
        period.getDeletedAt(),
        period.isScopeConfigured(),
        visible,
        visible.size() != period.getOfferings().size(),
        canManage(period, PermissionCode.ENROLLMENT_PERIOD_UPDATE),
        canManage(period, PermissionCode.ENROLLMENT_PERIOD_STATUS_UPDATE),
        canManage(period, PermissionCode.ENROLLMENT_PERIOD_DELETE));
  }
}
