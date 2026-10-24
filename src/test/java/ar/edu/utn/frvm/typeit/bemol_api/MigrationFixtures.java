package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
final class MigrationFixtures {

  private final JdbcTemplate jdbcTemplate;

  void insertShift(
      final UUID id, final UUID institutionId, final String name, final boolean active) {
    jdbcTemplate.update(
        """
        INSERT INTO shifts (
          shift_id, institution_id, name, active, created_at, updated_at
        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        name,
        active);
  }

  UUID firstInstitutionId() {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT institution_id FROM institutions ORDER BY institution_id LIMIT 1", UUID.class));
  }

  void insertTestInstitution(final UUID institutionId) {
    final UUID cityId =
        jdbcTemplate.queryForObject(
            "SELECT city_id FROM institutions ORDER BY institution_id LIMIT 1", UUID.class);
    jdbcTemplate.update(
        """
        INSERT INTO institutions (
          institution_id, city_id, name, active, created_at, updated_at
        ) VALUES (?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        institutionId,
        cityId,
        "Migration Institution " + institutionId);
  }

  void insertAcademicYear(final UUID id, final UUID institutionId, final int year) {
    insertAcademicYearWithStatus(id, institutionId, year, "ACTIVE");
  }

  void insertAcademicYearWithStatus(
      final UUID id, final UUID institutionId, final int year, final String status) {
    jdbcTemplate.update(
        """
        INSERT INTO academic_years (
          academic_year_id, institution_id, year, start_date, end_date,
          status, created_at, updated_at
        ) VALUES (
          ?, ?, ?, CASE WHEN ? = 'PLANNED' THEN NULL ELSE DATE '2031-03-01' END,
          CASE WHEN ? = 'PLANNED' THEN NULL ELSE DATE '2031-12-01' END,
          ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        """,
        id,
        institutionId,
        year,
        status,
        status,
        status);
  }

  void insertTrainingPath(final UUID id, final UUID institutionId) {
    insertTrainingPath(id, institutionId, "Migration Path " + id);
  }

  void insertTrainingPath(final UUID id, final UUID institutionId, final String name) {
    jdbcTemplate.update(
        """
        INSERT INTO training_paths (
          training_path_id, institution_id, name, active, created_at, updated_at
        ) VALUES (?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        name);
  }

  void insertStudyPlan(final UUID id, final UUID institutionId, final UUID trainingPathId) {
    insertStudyPlan(id, institutionId, trainingPathId, "Migration Plan " + id);
  }

  void insertStudyPlan(
      final UUID id, final UUID institutionId, final UUID trainingPathId, final String name) {
    jdbcTemplate.update(
        """
        INSERT INTO study_plans (
          study_plan_id, institution_id, training_path_id, name, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        trainingPathId,
        name);
  }

  void insertAcademicSpace(final UUID id, final UUID institutionId) {
    insertAcademicSpace(id, institutionId, "Migration Space " + id);
  }

  void insertAcademicSpace(final UUID id, final UUID institutionId, final String name) {
    insertAcademicSpace(id, institutionId, name, "INDIVIDUAL");
  }

  void insertAcademicSpace(
      final UUID id, final UUID institutionId, final String name, final String format) {
    jdbcTemplate.update(
        """
        INSERT INTO academic_spaces (
          academic_space_id, institution_id, name, type, format, active, created_at, updated_at
        ) VALUES (?, ?, ?, 'SUBJECT', ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        name,
        format);
  }

  void insertCourse(
      final UUID id,
      final UUID institutionId,
      final UUID studyPlanId,
      final UUID academicSpaceId,
      final UUID academicYearId) {
    final UUID studyPlanSpaceId = UUID.randomUUID();
    insertStudyPlanSpace(studyPlanSpaceId, institutionId, studyPlanId, academicSpaceId);
    jdbcTemplate.update(
        """
        INSERT INTO courses (
          course_id, institution_id, study_plan_space_id, training_path_id, academic_space_id, academic_year_id,
          academic_level_id, instrument_id, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        studyPlanSpaceId,
        jdbcTemplate.queryForObject(
            "SELECT training_path_id FROM study_plans WHERE study_plan_id = ?",
            UUID.class,
            studyPlanId),
        academicSpaceId,
        academicYearId);
  }

  void insertCourseClass(final UUID id, final UUID institutionId, final UUID courseId) {
    jdbcTemplate.update(
        """
        INSERT INTO course_classes (
          course_class_id, institution_id, course_id, class_number, created_at, updated_at
        ) VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        courseId);
  }

  void insertInstrument(final UUID id, final UUID institutionId, final String name) {
    jdbcTemplate.update(
        """
        INSERT INTO instruments (
          instrument_id, institution_id, name, active, created_at, updated_at
        ) VALUES (?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institutionId,
        name);
  }

  void insertStudyPlanSpace(
      final UUID id, final UUID institutionId, final UUID studyPlanId, final UUID academicSpaceId) {
    jdbcTemplate.update(
        """
        INSERT INTO study_plan_spaces (
          study_plan_space_id, institution_id, study_plan_id, academic_space_id,
          requirement_type, display_order, approval_mode, created_at, updated_at
        ) VALUES (
          ?, ?, ?, ?, 'REQUIRED', 1, 'PROMOTION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        """,
        id,
        institutionId,
        studyPlanId,
        academicSpaceId);
  }

  String randomDocumentNumber() {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT LPAD((floor(random() * 100000000))::bigint::text, 8, '0')", String.class));
  }

  void insertPerson(UUID personId, UUID institutionId, String documentNumber, boolean deleted) {
    jdbcTemplate.update(
        """
        INSERT INTO people (
          person_id, institution_id, document_number, first_name, last_name,
          email, created_at, updated_at, deleted
        ) VALUES (?, ?, ?, 'Ana', 'Garcia', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
        """,
        personId,
        institutionId,
        documentNumber,
        documentNumber + "@example.com",
        deleted);
  }
}
