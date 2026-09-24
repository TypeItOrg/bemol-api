package ar.edu.utn.frvm.typeit.boero_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class PersonGuardianMigrationIntegrationTest extends DatabaseMigrationTestSupport {

  @Test
  @DisplayName("Should drop the obsolete guardian tables")
  void shouldDropObsoleteGuardianTables() {
    final Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN ('guardian_profiles', 'student_guardians')
            """,
            Integer.class);

    assertThat(count).isZero();
  }

  @Test
  @DisplayName("Should allow a person without email so minors do not need their own")
  void shouldAllowPersonWithoutEmail() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID personId = UUID.randomUUID();

    try {
      assertThatCode(() -> insertMinorWithoutEmail(personId, institutionId))
          .doesNotThrowAnyException();
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE people SET email = '  ' WHERE person_id = ?", personId))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbcTemplate.update("DELETE FROM people WHERE person_id = ?", personId);
    }
  }

  @Test
  @DisplayName("Should link a tutor to a dependent and reject duplicated links")
  void shouldRejectDuplicatedLink() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID tutorId = UUID.randomUUID();
    final UUID dependentId = UUID.randomUUID();

    try {
      fixtures.insertPerson(tutorId, institutionId, fixtures.randomDocumentNumber(), false);
      insertMinorWithoutEmail(dependentId, institutionId);

      assertThatCode(() -> insertLink(institutionId, tutorId, dependentId, "FATHER"))
          .doesNotThrowAnyException();
      assertThatThrownBy(() -> insertLink(institutionId, tutorId, dependentId, "MOTHER"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      cleanUp(tutorId, dependentId);
    }
  }

  @Test
  @DisplayName("Should reject a person being its own guardian")
  void shouldRejectSelfGuardianship() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID personId = UUID.randomUUID();

    try {
      fixtures.insertPerson(personId, institutionId, fixtures.randomDocumentNumber(), false);

      assertThatThrownBy(() -> insertLink(institutionId, personId, personId, "FATHER"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      cleanUp(personId);
    }
  }

  @Test
  @DisplayName("Should reject an unknown relationship")
  void shouldRejectUnknownRelationship() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID tutorId = UUID.randomUUID();
    final UUID dependentId = UUID.randomUUID();

    try {
      fixtures.insertPerson(tutorId, institutionId, fixtures.randomDocumentNumber(), false);
      insertMinorWithoutEmail(dependentId, institutionId);

      assertThatThrownBy(() -> insertLink(institutionId, tutorId, dependentId, "UNCLE"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      cleanUp(tutorId, dependentId);
    }
  }

  @Test
  @DisplayName("Should reject a dependent that belongs to another institution")
  void shouldRejectCrossInstitutionDependent() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID otherInstitutionId = UUID.randomUUID();
    final UUID tutorId = UUID.randomUUID();
    final UUID dependentId = UUID.randomUUID();

    try {
      fixtures.insertTestInstitution(otherInstitutionId);
      fixtures.insertPerson(tutorId, institutionId, fixtures.randomDocumentNumber(), false);
      insertMinorWithoutEmail(dependentId, otherInstitutionId);

      assertThatThrownBy(() -> insertLink(institutionId, tutorId, dependentId, "FATHER"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      cleanUp(tutorId, dependentId);
      jdbcTemplate.update("DELETE FROM institutions WHERE institution_id = ?", otherInstitutionId);
    }
  }

  private void insertMinorWithoutEmail(final UUID personId, final UUID institutionId) {
    jdbcTemplate.update(
        """
        INSERT INTO people (
          person_id, institution_id, document_number, first_name, last_name,
          email, created_at, updated_at, deleted
        ) VALUES (?, ?, ?, 'Mateo', 'Gonzalez', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
        """,
        personId,
        institutionId,
        fixtures.randomDocumentNumber());
  }

  private void insertLink(
      final UUID institutionId,
      final UUID tutorId,
      final UUID dependentId,
      final String relationship) {
    jdbcTemplate.update(
        """
        INSERT INTO person_guardians (
          person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
          relationship, is_primary_contact, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        institutionId,
        tutorId,
        dependentId,
        relationship);
  }

  private void cleanUp(final UUID... personIds) {
    for (final UUID personId : personIds) {
      jdbcTemplate.update(
          "DELETE FROM person_guardians WHERE tutor_person_id = ? OR dependent_person_id = ?",
          personId,
          personId);
      jdbcTemplate.update("DELETE FROM people WHERE person_id = ?", personId);
    }
  }
}
