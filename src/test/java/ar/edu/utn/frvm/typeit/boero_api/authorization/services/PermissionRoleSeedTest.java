package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  JpaAuditingTestConfig.class,
  PermissionRoleSeed.class,
  InstitutionRoleProvisioner.class,
  AssignPersonSystemRoleUseCase.class,
  AuthorizationCacheInvalidator.class,
  SessionRevocationService.class
})
class PermissionRoleSeedTest {

  @MockitoBean private org.springframework.cache.CacheManager cacheManager;

  @Autowired private EntityManager entityManager;
  @Autowired private PermissionRoleSeed permissionRoleSeed;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private RolePermissionRepository rolePermissionRepository;

  @Test
  @DisplayName("Should seed permissions and system roles idempotently")
  void run_createsCatalog() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    assertThat(permissionRepository.count()).isEqualTo(PermissionCode.values().length);
    assertThat(
            roleRepository.findByScopeAndCodeAndInstitutionIsNull(
                RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name()))
        .isPresent();
    assertThat(
            roleRepository.findByScopeAndCodeAndInstitutionIsNull(
                RoleScope.PLATFORM, PlatformRoleCode.PLATFORM_ADMIN.name()))
        .isPresent();
  }

  @Test
  @DisplayName("Should assign people and granular academic permissions to institutional authority")
  void run_assignsNewPeoplePermissionsToAuthority() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    var authorityRole =
        roleRepository
            .findByScopeAndCodeAndInstitutionIsNull(
                RoleScope.INSTITUTION, SystemRoleCode.INSTITUTIONAL_AUTHORITY.name())
            .orElseThrow();
    Set<String> permissionCodes =
        rolePermissionRepository.findByRole_Id(authorityRole.getId()).stream()
            .map(rp -> rp.getPermission().getCode())
            .collect(Collectors.toSet());

    assertThat(permissionCodes)
        .contains(
            PermissionCode.INSTITUTION_PERSON_READ_ANY.getCode(),
            PermissionCode.INSTITUTION_PERSON_CREATE.getCode(),
            PermissionCode.INSTITUTION_PERSON_UPDATE_ANY.getCode(),
            PermissionCode.INSTITUTION_PERSON_DELETE.getCode(),
            PermissionCode.ACADEMIC_YEAR_CREATE.getCode(),
            PermissionCode.ACADEMIC_YEAR_UPDATE.getCode(),
            PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE.getCode(),
            PermissionCode.ACADEMIC_YEAR_READ.getCode(),
            PermissionCode.TRAINING_PATH_CREATE.getCode(),
            PermissionCode.TRAINING_PATH_UPDATE.getCode(),
            PermissionCode.TRAINING_PATH_STATUS_UPDATE.getCode(),
            PermissionCode.TRAINING_PATH_READ.getCode(),
            PermissionCode.STUDY_PLAN_CREATE.getCode(),
            PermissionCode.STUDY_PLAN_UPDATE.getCode(),
            PermissionCode.STUDY_PLAN_STATUS_UPDATE.getCode(),
            PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE.getCode(),
            PermissionCode.STUDY_PLAN_READ.getCode(),
            PermissionCode.ACADEMIC_SPACE_CREATE.getCode(),
            PermissionCode.ACADEMIC_SPACE_UPDATE.getCode(),
            PermissionCode.ACADEMIC_SPACE_STATUS_UPDATE.getCode(),
            PermissionCode.ACADEMIC_SPACE_READ.getCode(),
            PermissionCode.INSTRUMENT_CREATE.getCode(),
            PermissionCode.INSTRUMENT_UPDATE.getCode(),
            PermissionCode.INSTRUMENT_STATUS_UPDATE.getCode(),
            PermissionCode.INSTRUMENT_READ.getCode(),
            PermissionCode.ENROLLMENT_PERIOD_CREATE.getCode(),
            PermissionCode.ENROLLMENT_PERIOD_UPDATE.getCode(),
            PermissionCode.ENROLLMENT_PERIOD_STATUS_UPDATE.getCode(),
            PermissionCode.ENROLLMENT_PERIOD_READ.getCode(),
            PermissionCode.SHIFT_CREATE.getCode(),
            PermissionCode.SHIFT_UPDATE.getCode(),
            PermissionCode.SHIFT_STATUS_UPDATE.getCode(),
            PermissionCode.SHIFT_READ.getCode(),
            PermissionCode.ACADEMIC_OFFER_READ.getCode());
  }

  @Test
  @DisplayName("Should grant academic offer access to applicants")
  void run_assignsAcademicOfferPermissionToApplicants() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    final var applicantRole =
        roleRepository
            .findByScopeAndCodeAndInstitutionIsNull(
                RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name())
            .orElseThrow();

    assertThat(rolePermissionRepository.findByRole_Id(applicantRole.getId()))
        .extracting(rolePermission -> rolePermission.getPermission().getCode())
        .containsExactlyInAnyOrder(PermissionCode.ACADEMIC_OFFER_READ.getCode());
  }

  @Test
  @DisplayName("Should assign enrollment application resolution permissions to administrative role")
  void run_assignsEnrollmentApplicationPermissionsToAdministrative() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    var administrativeRole =
        roleRepository
            .findByScopeAndCodeAndInstitutionIsNull(
                RoleScope.INSTITUTION, SystemRoleCode.ADMINISTRATIVE.name())
            .orElseThrow();
    Set<String> permissionCodes =
        rolePermissionRepository.findByRole_Id(administrativeRole.getId()).stream()
            .map(rp -> rp.getPermission().getCode())
            .collect(Collectors.toSet());

    assertThat(permissionCodes)
        .contains(
            PermissionCode.ENROLLMENT_APPLICATION_READ.getCode(),
            PermissionCode.ENROLLMENT_APPLICATION_APPROVE.getCode(),
            PermissionCode.ENROLLMENT_APPLICATION_REJECT.getCode());
  }

  @Test
  @DisplayName("Should let administrative staff and authorities review guardian links")
  void run_assignsGuardianLinkReviewToInstitutionalReviewers() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    for (final SystemRoleCode code :
        List.of(SystemRoleCode.ADMINISTRATIVE, SystemRoleCode.INSTITUTIONAL_AUTHORITY)) {
      final var role =
          roleRepository
              .findByScopeAndCodeAndInstitutionIsNull(RoleScope.INSTITUTION, code.name())
              .orElseThrow();

      assertThat(rolePermissionRepository.findByRole_Id(role.getId()))
          .extracting(rolePermission -> rolePermission.getPermission().getCode())
          .contains(PermissionCode.GUARDIAN_LINK_REVIEW.getCode());
    }
  }

  @Test
  @DisplayName("Should grant guardians the applicant permissions plus dependent management")
  void run_assignsDependentManagementToGuardians() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    final var guardianRole =
        roleRepository
            .findByScopeAndCodeAndInstitutionIsNull(
                RoleScope.INSTITUTION, SystemRoleCode.GUARDIAN.name())
            .orElseThrow();

    assertThat(rolePermissionRepository.findByRole_Id(guardianRole.getId()))
        .extracting(rolePermission -> rolePermission.getPermission().getCode())
        .containsExactlyInAnyOrder(
            PermissionCode.ACADEMIC_OFFER_READ.getCode(),
            PermissionCode.STUDY_PLAN_READ.getCode(),
            PermissionCode.ACADEMIC_YEAR_READ.getCode(),
            PermissionCode.GUARDIAN_DEPENDENT_MANAGE.getCode());
  }

  @Test
  @DisplayName("Should provision the guardian role in every institution")
  void run_provisionsGuardianRoleForInstitutions() {
    final Institution institution = InstitutionalTestData.createInstitution(entityManager, "boero");
    entityManager.flush();

    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());

    final var guardianRole =
        roleRepository
            .findByScopeAndCodeAndInstitution_Id(
                RoleScope.INSTITUTION, SystemRoleCode.GUARDIAN.name(), institution.getId())
            .orElseThrow();

    assertThat(rolePermissionRepository.findByRole_Id(guardianRole.getId()))
        .extracting(rolePermission -> rolePermission.getPermission().getCode())
        .contains(PermissionCode.GUARDIAN_DEPENDENT_MANAGE.getCode());
  }
}
