package ar.edu.utn.frvm.typeit.boero_api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

@IntegrationTest
class DependentApplicantRoleBackfillMigrationIntegrationTest extends DatabaseMigrationTestSupport {

  private static final String BACKFILL_MIGRATION =
      "db/migration/20260930185419__backfill_applicant_role_for_dependents.sql";

  private final UUID institutionId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID roleLessDependentId = UUID.randomUUID();
  private final UUID studentDependentId = UUID.randomUUID();
  private final UUID unrelatedPersonId = UUID.randomUUID();
  private UUID applicantRoleId;
  private UUID studentRoleId;

  @BeforeEach
  void seedLegacyData() {
    fixtures.insertTestInstitution(institutionId);
    applicantRoleId = insertRole("APPLICANT");
    studentRoleId = insertRole("STUDENT");

    for (final UUID personId :
        List.of(tutorId, roleLessDependentId, studentDependentId, unrelatedPersonId)) {
      fixtures.insertPerson(personId, institutionId, fixtures.randomDocumentNumber(), false);
    }

    insertLink(roleLessDependentId);
    insertLink(studentDependentId);
    assignRole(studentDependentId, studentRoleId);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM person_role_assignments WHERE institution_id = ?", institutionId);
    jdbcTemplate.update("DELETE FROM person_guardians WHERE institution_id = ?", institutionId);
    jdbcTemplate.update("DELETE FROM people WHERE institution_id = ?", institutionId);
    jdbcTemplate.update("DELETE FROM roles WHERE institution_id = ?", institutionId);
    jdbcTemplate.update("DELETE FROM institutions WHERE institution_id = ?", institutionId);
  }

  @Test
  @DisplayName("Should grant the applicant role to dependents that have no role")
  void grantsApplicantRoleToRoleLessDependents() {
    runBackfill();

    assertThat(roleIdsOf(roleLessDependentId)).containsExactly(applicantRoleId);
  }

  @Test
  @DisplayName("Should keep the roles of dependents that already have one")
  void keepsExistingRoles() {
    runBackfill();

    assertThat(roleIdsOf(studentDependentId)).containsExactly(studentRoleId);
  }

  @Test
  @DisplayName("Should not touch people that are not dependents")
  void ignoresPeopleWithoutGuardianshipLink() {
    runBackfill();

    assertThat(roleIdsOf(unrelatedPersonId)).isEmpty();
    assertThat(roleIdsOf(tutorId)).isEmpty();
  }

  @Test
  @DisplayName("Should be safe to run more than once")
  void isIdempotent() {
    runBackfill();
    runBackfill();

    assertThat(roleIdsOf(roleLessDependentId)).containsExactly(applicantRoleId);
  }

  private void runBackfill() {
    try {
      final String sql =
          StreamUtils.copyToString(
              new ClassPathResource(BACKFILL_MIGRATION).getInputStream(), StandardCharsets.UTF_8);
      jdbcTemplate.execute(sql);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private UUID insertRole(final String code) {
    final UUID roleId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO roles (
          role_id, institution_id, scope, code, name, is_system, created_at, updated_at
        ) VALUES (?, ?, 'INSTITUTION', ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        roleId,
        institutionId,
        code,
        code);

    return roleId;
  }

  private void assignRole(final UUID personId, final UUID roleId) {
    jdbcTemplate.update(
        """
        INSERT INTO person_role_assignments (
          person_role_assignment_id, person_id, role_id, institution_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        personId,
        roleId,
        institutionId);
  }

  private void insertLink(final UUID dependentId) {
    jdbcTemplate.update(
        """
        INSERT INTO person_guardians (
          person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
          relationship, is_primary_contact, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'FATHER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        institutionId,
        tutorId,
        dependentId);
  }

  private List<UUID> roleIdsOf(final UUID personId) {
    return jdbcTemplate.queryForList(
        "SELECT role_id FROM person_role_assignments WHERE person_id = ? AND institution_id = ?",
        UUID.class,
        personId,
        institutionId);
  }
}
