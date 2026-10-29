package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstitutionalCapabilitiesTest {
  private static final Map<PermissionCode, PermissionAccess> OWN_PERMISSIONS =
      Map.of(
          PermissionCode.OWN_ENROLLMENT_APPLICATION_READ, PermissionAccess.institution(),
          PermissionCode.OWN_ENROLLMENT_APPLICATION_MANAGE, PermissionAccess.institution());

  @ParameterizedTest
  @CsvSource({
    "APPLICANT,true", "STUDENT,true", "INSTITUTIONAL_AUTHORITY,false",
    "ADMINISTRATIVE,false", "TEACHER,false", "REPRESENTATIVE,false"
  })
  void ownApplicationsRequireAPersonalAcademicRoleEvenWithAllPermissions(
      final SystemRoleCode role, final boolean allowed) {
    final var capabilities = resolve(Set.of(role), OWN_PERMISSIONS);

    assertThat(capabilities.canViewOwnEnrollmentApplications()).isEqualTo(allowed);
    assertThat(capabilities.canManageOwnEnrollmentApplications()).isEqualTo(allowed);
  }

  @Test
  void anAdministratorWhoIsAlsoAStudentKeepsPersonalAccess() {
    final var capabilities =
        resolve(
            Set.of(SystemRoleCode.INSTITUTIONAL_AUTHORITY, SystemRoleCode.STUDENT),
            OWN_PERMISSIONS);

    assertThat(capabilities.canViewOwnEnrollmentApplications()).isTrue();
    assertThat(capabilities.canManageOwnEnrollmentApplications()).isTrue();
  }

  @Test
  void aPersonalRoleDoesNotReplaceReadAndManagePermissions() {
    final var denied = resolve(Set.of(SystemRoleCode.APPLICANT), Map.of());
    final var readOnly =
        resolve(
            Set.of(SystemRoleCode.APPLICANT),
            Map.of(PermissionCode.OWN_ENROLLMENT_APPLICATION_READ, PermissionAccess.institution()));
    final var manageOnly =
        resolve(
            Set.of(SystemRoleCode.APPLICANT),
            Map.of(
                PermissionCode.OWN_ENROLLMENT_APPLICATION_MANAGE, PermissionAccess.institution()));
    final var scopedRead =
        resolve(
            Set.of(SystemRoleCode.APPLICANT),
            Map.of(PermissionCode.OWN_ENROLLMENT_APPLICATION_READ, PermissionAccess.none()));

    assertThat(denied.canViewOwnEnrollmentApplications()).isFalse();
    assertThat(denied.canManageOwnEnrollmentApplications()).isFalse();
    assertThat(readOnly.canViewOwnEnrollmentApplications()).isTrue();
    assertThat(readOnly.canManageOwnEnrollmentApplications()).isFalse();
    assertThat(manageOnly.canManageOwnEnrollmentApplications()).isFalse();
    assertThat(scopedRead.canViewOwnEnrollmentApplications()).isFalse();
  }

  private InstitutionalCapabilities resolve(
      final Set<SystemRoleCode> roles, final Map<PermissionCode, PermissionAccess> scopes) {
    return InstitutionalCapabilities.from(
        new InstitutionalAuthoritySnapshot(
            scopes.keySet(), List.of("Rol renombrado"), scopes, roles));
  }
}
