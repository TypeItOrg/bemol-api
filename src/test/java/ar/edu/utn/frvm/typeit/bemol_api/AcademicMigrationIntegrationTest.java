package ar.edu.utn.frvm.typeit.bemol_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class AcademicMigrationIntegrationTest extends DatabaseMigrationTestSupport {

  @Test
  @DisplayName("Should apply academic soft-delete state and partial uniqueness")
  void shouldApplyAcademicSoftDeleteStateAndPartialUniqueness() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID deletedPathId = UUID.randomUUID();
    final UUID currentPathId = UUID.randomUUID();
    final String name = "Trayecto reutilizable " + deletedPathId;
    try {
      jdbcTemplate.update(
          """
          INSERT INTO training_paths (
            training_path_id, institution_id, name, active, created_at, updated_at
          ) VALUES (?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          deletedPathId,
          institutionId,
          name);
      jdbcTemplate.update(
          "UPDATE training_paths SET deleted_at = CURRENT_TIMESTAMP WHERE training_path_id = ?",
          deletedPathId);
      jdbcTemplate.update(
          """
          INSERT INTO training_paths (
            training_path_id, institution_id, name, active, created_at, updated_at
          ) VALUES (?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          currentPathId,
          institutionId,
          name);

      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE training_paths SET deleted_at = NULL WHERE training_path_id = ?",
                      deletedPathId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE training_paths SET deleted_at = CURRENT_TIMESTAMP WHERE training_path_id = ?",
                      currentPathId))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM training_paths WHERE training_path_id IN (?, ?)",
          deletedPathId,
          currentPathId);
    }
  }

  @Test
  @DisplayName("Should enforce shift name uniqueness only for non-deleted shifts")
  void shouldEnforceShiftNameUniquenessOnlyForNonDeletedShifts() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID deletedShiftId = UUID.randomUUID();
    final UUID currentShiftId = UUID.randomUUID();
    final String name = "Turno reutilizable " + deletedShiftId;
    try {
      fixtures.insertShift(deletedShiftId, institutionId, name, false);
      jdbcTemplate.update(
          "UPDATE shifts SET deleted_at = CURRENT_TIMESTAMP WHERE shift_id = ?", deletedShiftId);
      fixtures.insertShift(currentShiftId, institutionId, name, true);

      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE shifts SET deleted_at = NULL WHERE shift_id = ?", deletedShiftId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE shifts SET deleted_at = CURRENT_TIMESTAMP WHERE shift_id = ?",
                      currentShiftId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  fixtures.insertShift(
                      UUID.randomUUID(), institutionId, "  Turno   reutilizable ", true))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM shifts WHERE shift_id IN (?, ?)", deletedShiftId, currentShiftId);
    }
  }

  @Test
  @DisplayName("Should record lifecycle events for shifts")
  void shouldRecordLifecycleEventsForShifts() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID shiftId = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();
    try {
      fixtures.insertShift(shiftId, institutionId, "Turno auditado " + shiftId, true);
      jdbcTemplate.update(
          """
          INSERT INTO academic_lifecycle_events (
            academic_lifecycle_event_id, institution_id, resource_type, resource_id,
            action, actor_type, actor_id, created_at
          ) VALUES (?, ?, 'SHIFT', ?, 'DELETE', 'INSTITUTION', ?, CURRENT_TIMESTAMP)
          """,
          eventId,
          institutionId,
          shiftId,
          institutionId);

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT resource_type FROM academic_lifecycle_events WHERE academic_lifecycle_event_id = ?",
                  String.class,
                  eventId))
          .isEqualTo("SHIFT");
    } finally {
      jdbcTemplate.update(
          "DELETE FROM academic_lifecycle_events WHERE academic_lifecycle_event_id = ?", eventId);
      jdbcTemplate.update("DELETE FROM shifts WHERE shift_id = ?", shiftId);
    }
  }

  @Test
  @DisplayName("Should allow an open-ended study plan validity")
  void shouldAllowAnOpenEndedStudyPlanValidity() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID trainingPathId = UUID.randomUUID();
    final UUID studyPlanId = UUID.randomUUID();
    try {
      fixtures.insertTrainingPath(trainingPathId, institutionId);
      jdbcTemplate.update(
          """
          INSERT INTO study_plans (
            study_plan_id, institution_id, training_path_id, name,
            effective_from, status, created_at, updated_at
          ) VALUES (?, ?, ?, ?, DATE '2031-03-01', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          studyPlanId,
          institutionId,
          trainingPathId,
          "Open-ended Plan " + studyPlanId);

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT effective_to FROM study_plans WHERE study_plan_id = ?",
                  LocalDate.class,
                  studyPlanId))
          .isNull();
    } finally {
      jdbcTemplate.update("DELETE FROM study_plans WHERE study_plan_id = ?", studyPlanId);
      jdbcTemplate.update("DELETE FROM training_paths WHERE training_path_id = ?", trainingPathId);
    }
  }

  @Test
  @DisplayName("Should reject a study plan end date without a start date")
  void shouldRejectAStudyPlanEndDateWithoutAStartDate() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID trainingPathId = UUID.randomUUID();
    final UUID studyPlanId = UUID.randomUUID();
    try {
      fixtures.insertTrainingPath(trainingPathId, institutionId);
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      """
                      INSERT INTO study_plans (
                        study_plan_id, institution_id, training_path_id, name,
                        effective_to, status, created_at, updated_at
                      ) VALUES (?, ?, ?, ?, DATE '2031-12-15', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                      """,
                      studyPlanId,
                      institutionId,
                      trainingPathId,
                      "Missing Start Plan " + studyPlanId))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update("DELETE FROM study_plans WHERE study_plan_id = ?", studyPlanId);
      jdbcTemplate.update("DELETE FROM training_paths WHERE training_path_id = ?", trainingPathId);
    }
  }

  @Test
  @DisplayName("Should allow only one active academic year per institution")
  void shouldAllowOnlyOneActiveAcademicYearPerInstitution() {
    final UUID institutionId = UUID.randomUUID();
    fixtures.insertTestInstitution(institutionId);
    final UUID firstYearId = UUID.randomUUID();
    final UUID secondYearId = UUID.randomUUID();
    try {
      fixtures.insertAcademicYear(firstYearId, institutionId, 2031);
      assertThatThrownBy(() -> fixtures.insertAcademicYear(secondYearId, institutionId, 2032))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM academic_years WHERE academic_year_id IN (?, ?)", firstYearId, secondYearId);
      jdbcTemplate.update("DELETE FROM institutions WHERE institution_id = ?", institutionId);
    }
  }

  @Test
  @DisplayName("Should enforce academic space format values and name uniqueness per format")
  void shouldEnforceAcademicSpaceFormatConstraintAndUniqueness() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID firstSpaceId = UUID.randomUUID();
    final UUID secondSpaceId = UUID.randomUUID();
    try {
      fixtures.insertAcademicSpace(firstSpaceId, institutionId, "Format Space");
      assertThatThrownBy(
              () ->
                  fixtures.insertAcademicSpace(
                      UUID.randomUUID(), institutionId, "Invalid Format Space", "HYBRID"))
          .isInstanceOf(DataIntegrityViolationException.class);
      fixtures.insertAcademicSpace(secondSpaceId, institutionId, "Format Space", "GRUPAL");
    } finally {
      jdbcTemplate.update(
          "DELETE FROM academic_spaces WHERE academic_space_id IN (?, ?)",
          firstSpaceId,
          secondSpaceId);
    }
  }
}
