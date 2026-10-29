package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    requiredProperties = {
      "canViewOwnEnrollmentApplications",
      "canManageOwnEnrollmentApplications",
      "canViewOwnFormation",
      "canViewTeaching",
      "canUpdateTeachingAcademicStatus"
    })
public record InstitutionalCapabilities(
    boolean canViewOwnEnrollmentApplications,
    boolean canManageOwnEnrollmentApplications,
    boolean canViewOwnFormation,
    boolean canViewTeaching,
    boolean canUpdateTeachingAcademicStatus) {
  public static final InstitutionalCapabilities NONE =
      new InstitutionalCapabilities(false, false, false, false, false);

  public static InstitutionalCapabilities from(final InstitutionalAuthoritySnapshot authorities) {
    final var roles = authorities.systemRoleCodes();
    final boolean canApply =
        roles.contains(SystemRoleCode.APPLICANT) || roles.contains(SystemRoleCode.STUDENT);
    final boolean canReadApplications =
        canApply && granted(authorities, PermissionCode.OWN_ENROLLMENT_APPLICATION_READ);
    final boolean canReadTeaching =
        roles.contains(SystemRoleCode.TEACHER)
            && granted(authorities, PermissionCode.TEACHING_READ);
    return new InstitutionalCapabilities(
        canReadApplications,
        canReadApplications
            && granted(authorities, PermissionCode.OWN_ENROLLMENT_APPLICATION_MANAGE),
        roles.contains(SystemRoleCode.STUDENT)
            && granted(authorities, PermissionCode.OWN_FORMATION_READ),
        canReadTeaching,
        canReadTeaching && granted(authorities, PermissionCode.TEACHING_ACADEMIC_STATUS_UPDATE));
  }

  private static boolean granted(
      final InstitutionalAuthoritySnapshot authorities, final PermissionCode permission) {
    return authorities
        .permissionScopes()
        .getOrDefault(permission, PermissionAccess.none())
        .institutional();
  }
}
