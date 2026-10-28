package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoleTest {
  @ParameterizedTest
  @CsvSource({
    "APPLICANT,REPRESENTATIVE,false",
    "REPRESENTATIVE,APPLICANT,false",
    "APPLICANT,TEACHER,true",
    "TEACHER,APPLICANT,true",
    "APPLICANT,STUDENT,true",
    "STUDENT,APPLICANT,true",
    "APPLICANT,ADMINISTRATIVE,true",
    "ADMINISTRATIVE,APPLICANT,true",
    "REPRESENTATIVE,TEACHER,false",
    "TEACHER,REPRESENTATIVE,false",
    "STUDENT,REPRESENTATIVE,false",
    "REPRESENTATIVE,STUDENT,false",
    "APPLICANT,APPLICANT,false",
    "REPRESENTATIVE,REPRESENTATIVE,false"
  })
  void replacesOnlyConflictingApplicantRoles(
      final SystemRoleCode assigned, final SystemRoleCode current, final boolean replaces) {
    final var role = Role.builder().code(assigned.name()).system(true).build();
    final var existing = Role.builder().code(current.name()).system(true).build();

    assertThat(role.replacesOnAssignment(existing)).isEqualTo(replaces);
  }

  @ParameterizedTest
  @CsvSource({"REPRESENTATIVE", "APPLICANT", "STAFF"})
  void applicantStillReplacesCustomRoles(final String code) {
    final var applicant = Role.builder().code("APPLICANT").system(true).build();
    final var custom = Role.builder().code(code).system(false).build();

    assertThat(applicant.replacesOnAssignment(custom)).isTrue();
    assertThat(custom.replacesOnAssignment(applicant)).isTrue();
  }
}
