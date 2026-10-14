package ar.edu.utn.frvm.typeit.boero_api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class TenantMigrationIntegrationTest extends DatabaseMigrationTestSupport {

  @ParameterizedTest
  @CsvSource({
    "1234567,Ana,Garcia,people_document_number_format",
    "12345678,AB,Garcia,people_first_name_length",
    "12345678,Ana,AB,people_last_name_length"
  })
  void rejectsMalformedPersonDataThroughTheNamedPostgresConstraint(
      String document, String firstName, String lastName, String constraint) {
    final var institutionId = fixtures.firstInstitutionId();
    final var id = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
        INSERT INTO people (
          person_id, institution_id, document_number, first_name, last_name, email,
          deleted, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, 'ana@example.com', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
                    id,
                    institutionId,
                    document,
                    firstName,
                    lastName))
        .isInstanceOf(DataIntegrityViolationException.class)
        .rootCause()
        .hasMessageContaining("violates check constraint \"" + constraint + "\"");
  }

  @Test
  @DisplayName("Should enforce document uniqueness only for active people")
  void shouldEnforceDocumentUniquenessOnlyForActivePeople() {
    UUID institutionId = fixtures.firstInstitutionId();
    UUID deletedPersonId = UUID.randomUUID();
    UUID activePersonId = UUID.randomUUID();
    String documentNumber = fixtures.randomDocumentNumber();

    fixtures.insertPerson(deletedPersonId, institutionId, documentNumber, true);
    try {
      fixtures.insertPerson(activePersonId, institutionId, documentNumber, false);
      assertThatThrownBy(
              () -> fixtures.insertPerson(UUID.randomUUID(), institutionId, documentNumber, false))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM people WHERE person_id IN (?, ?)", deletedPersonId, activePersonId);
    }
  }

  @Test
  @DisplayName("Should reject a course teacher that belongs to another institution")
  void shouldRejectCrossInstitutionCourseTeacher() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID otherInstitutionId = UUID.randomUUID();
    final UUID trainingPathId = UUID.randomUUID();
    final UUID studyPlanId = UUID.randomUUID();
    final UUID academicSpaceId = UUID.randomUUID();
    final UUID academicYearId = UUID.randomUUID();
    final UUID courseId = UUID.randomUUID();
    final UUID courseClassId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    final UUID courseClassTeacherId = UUID.randomUUID();
    try {
      fixtures.insertTestInstitution(otherInstitutionId);
      fixtures.insertTrainingPath(trainingPathId, institutionId);
      fixtures.insertStudyPlan(studyPlanId, institutionId, trainingPathId);
      fixtures.insertAcademicSpace(academicSpaceId, institutionId);
      fixtures.insertAcademicYearWithStatus(academicYearId, institutionId, 2099, "PLANNED");
      fixtures.insertCourse(courseId, institutionId, studyPlanId, academicSpaceId, academicYearId);
      fixtures.insertCourseClass(courseClassId, institutionId, courseId);
      fixtures.insertPerson(personId, otherInstitutionId, fixtures.randomDocumentNumber(), false);

      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      """
                      INSERT INTO course_class_teachers (
                        course_class_teacher_id, institution_id, course_class_id, person_id,
                        created_at, updated_at
                      ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                      """,
                      courseClassTeacherId,
                      institutionId,
                      courseClassId,
                      personId))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM course_class_teachers WHERE course_class_teacher_id = ?",
          courseClassTeacherId);
      jdbcTemplate.update("DELETE FROM course_classes WHERE course_class_id = ?", courseClassId);
      jdbcTemplate.update("DELETE FROM courses WHERE course_id = ?", courseId);
      jdbcTemplate.update("DELETE FROM study_plan_spaces WHERE study_plan_id = ?", studyPlanId);
      jdbcTemplate.update("DELETE FROM people WHERE person_id = ?", personId);
      jdbcTemplate.update("DELETE FROM academic_years WHERE academic_year_id = ?", academicYearId);
      jdbcTemplate.update(
          "DELETE FROM academic_spaces WHERE academic_space_id = ?", academicSpaceId);
      jdbcTemplate.update("DELETE FROM study_plans WHERE study_plan_id = ?", studyPlanId);
      jdbcTemplate.update("DELETE FROM training_paths WHERE training_path_id = ?", trainingPathId);
      jdbcTemplate.update("DELETE FROM institutions WHERE institution_id = ?", otherInstitutionId);
    }
  }

  @Test
  @DisplayName("Should reject a study plan space that crosses institutions")
  void shouldRejectCrossInstitutionStudyPlanSpace() {
    final UUID firstInstitutionId = fixtures.firstInstitutionId();
    final UUID secondInstitutionId = UUID.randomUUID();
    final UUID trainingPathId = UUID.randomUUID();
    final UUID studyPlanId = UUID.randomUUID();
    final UUID academicSpaceId = UUID.randomUUID();
    final UUID studyPlanSpaceId = UUID.randomUUID();
    try {
      fixtures.insertTestInstitution(secondInstitutionId);
      fixtures.insertTrainingPath(trainingPathId, firstInstitutionId);
      fixtures.insertStudyPlan(studyPlanId, firstInstitutionId, trainingPathId);
      fixtures.insertAcademicSpace(academicSpaceId, secondInstitutionId);
      assertThatThrownBy(
              () ->
                  fixtures.insertStudyPlanSpace(
                      studyPlanSpaceId, firstInstitutionId, studyPlanId, academicSpaceId))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM study_plan_spaces WHERE study_plan_space_id = ?", studyPlanSpaceId);
      jdbcTemplate.update(
          "DELETE FROM academic_spaces WHERE academic_space_id = ?", academicSpaceId);
      jdbcTemplate.update("DELETE FROM study_plans WHERE study_plan_id = ?", studyPlanId);
      jdbcTemplate.update("DELETE FROM training_paths WHERE training_path_id = ?", trainingPathId);
      jdbcTemplate.update("DELETE FROM institutions WHERE institution_id = ?", secondInstitutionId);
    }
  }
}
