package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionCodeDependencyTest {

  @Test
  void courseSelectionReadIncludesTheApplicationDetailItNeeds() {
    assertThat(
            PermissionCode.withRequiredPermissions(
                Set.of(PermissionCode.ENROLLMENT_APPLICATION_COURSE_READ)))
        .containsExactlyInAnyOrder(
            PermissionCode.ENROLLMENT_APPLICATION_COURSE_READ,
            PermissionCode.ENROLLMENT_APPLICATION_READ);
  }

  @Test
  @DisplayName("Should include the people read permission for people actions")
  void withRequiredPermissions_includesPeopleReadPermission() {
    Set<PermissionCode> expanded =
        PermissionCode.withRequiredPermissions(Set.of(PermissionCode.INSTITUTION_PERSON_DELETE));

    assertThat(expanded)
        .containsExactlyInAnyOrder(
            PermissionCode.INSTITUTION_PERSON_DELETE, PermissionCode.INSTITUTION_PERSON_READ_ANY);
  }

  @Test
  @DisplayName("Should include both module bases for role assignment")
  void withRequiredPermissions_includesBothModuleBases() {
    Set<PermissionCode> expanded =
        PermissionCode.withRequiredPermissions(Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN));

    assertThat(expanded)
        .containsExactlyInAnyOrder(
            PermissionCode.INSTITUTION_ROLE_ASSIGN,
            PermissionCode.INSTITUTION_PERSON_READ_ANY,
            PermissionCode.INSTITUTION_ROLE_READ);
  }

  @Test
  @DisplayName("Should include the read permission for every granular academic action")
  void withRequiredPermissions_includesAcademicReadPermissions() {
    Set<PermissionCode> expanded =
        PermissionCode.withRequiredPermissions(
            Set.of(
                PermissionCode.ACADEMIC_YEAR_CREATE,
                PermissionCode.ACADEMIC_YEAR_UPDATE,
                PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE,
                PermissionCode.TRAINING_PATH_CREATE,
                PermissionCode.TRAINING_PATH_UPDATE,
                PermissionCode.TRAINING_PATH_STATUS_UPDATE,
                PermissionCode.STUDY_PLAN_CREATE,
                PermissionCode.STUDY_PLAN_UPDATE,
                PermissionCode.STUDY_PLAN_STATUS_UPDATE,
                PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
                PermissionCode.ACADEMIC_SPACE_CREATE,
                PermissionCode.ACADEMIC_SPACE_UPDATE,
                PermissionCode.ACADEMIC_SPACE_STATUS_UPDATE,
                PermissionCode.INSTRUMENT_CREATE,
                PermissionCode.INSTRUMENT_UPDATE,
                PermissionCode.INSTRUMENT_STATUS_UPDATE,
                PermissionCode.SHIFT_CREATE,
                PermissionCode.SHIFT_UPDATE,
                PermissionCode.SHIFT_STATUS_UPDATE));

    assertThat(expanded)
        .contains(
            PermissionCode.ACADEMIC_YEAR_READ,
            PermissionCode.TRAINING_PATH_READ,
            PermissionCode.STUDY_PLAN_READ,
            PermissionCode.ACADEMIC_SPACE_READ,
            PermissionCode.INSTRUMENT_READ,
            PermissionCode.SHIFT_READ);
  }
}
