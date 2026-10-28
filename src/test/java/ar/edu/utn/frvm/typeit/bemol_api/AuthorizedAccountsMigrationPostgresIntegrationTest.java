package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@IntegrationTest
class AuthorizedAccountsMigrationPostgresIntegrationTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void supportsFreshInstallAndUpgradeWithDeferredSubjects(final boolean upgrade) {
    final var database =
        new PostgresTestDatabase(
            "postgres:18-alpine", upgrade ? "accounts_upgrade" : "accounts_fresh");
    final var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    migrations(jdbc, upgrade ? "20261025205535" : "latest").migrate();
    final var fixtures = new MigrationFixtures(jdbc);
    final UUID institution = fixtures.firstInstitutionId();
    final UUID tutor = UUID.randomUUID(), dependent = UUID.randomUUID(), legacy = UUID.randomUUID();
    fixtures.insertPerson(tutor, institution, fixtures.randomDocumentNumber(), false);
    fixtures.insertPerson(dependent, institution, "99119901", false);
    if (upgrade) {
      jdbc.update(
          "INSERT INTO person_guardians(person_guardian_id,institution_id,tutor_person_id,dependent_person_id,relationship,status,created_at,updated_at) VALUES(?,?,?,?,'FATHER','PENDING',now(),now())",
          legacy,
          institution,
          tutor,
          dependent);
      jdbc.update(
          "INSERT INTO person_access_invitations(invitation_id,person_id,representation_id,email,token_hash,expires_at) VALUES(?,?,?,'legacy@example.test','legacy-hash',now()+interval '24 hours')",
          UUID.randomUUID(),
          dependent,
          legacy);
    }
    final var current = migrations(jdbc, "latest");
    current.migrate();
    assertThat(current.validateWithResult().validationSuccessful).isTrue();
    if (upgrade) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT declared_document_number FROM person_guardians WHERE person_guardian_id=?",
                  String.class,
                  legacy))
          .isEqualTo("99119901");
      assertThat(
              jdbc.queryForObject(
                  "SELECT purpose FROM person_access_invitations WHERE person_id=?",
                  String.class,
                  dependent))
          .isEqualTo("INITIAL_ACCESS");
      assertThat(
              jdbc.queryForObject(
                  "SELECT issued_at IS NOT NULL FROM person_access_invitations WHERE person_id=?",
                  Boolean.class,
                  dependent))
          .isTrue();
    }
    final String unreserved = "99119902";
    insertPending(jdbc, institution, tutor, unreserved);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM people WHERE document_number=? AND institution_id=?",
                Long.class,
                unreserved,
                institution))
        .isZero();
    assertThatThrownBy(() -> insertPending(jdbc, institution, tutor, unreserved))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("person_guardians_pending_document_unique");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE person_guardians SET status='ACTIVE' WHERE declared_document_number=? AND institution_id=?",
                    unreserved,
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("person_guardians_active_subject_check");
    insertPending(jdbc, institution, dependent, unreserved);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM person_guardians WHERE declared_document_number=? AND institution_id=?",
                Long.class,
                unreserved,
                institution))
        .isEqualTo(2);
  }

  private void insertPending(
      final JdbcTemplate jdbc, final UUID institution, final UUID tutor, final String document) {
    jdbc.update(
        "INSERT INTO person_guardians(person_guardian_id,institution_id,tutor_person_id,declared_document_number,request_mode,relationship,status,created_at,updated_at) VALUES(?,?,?,?,'NEW','LEGAL_GUARDIAN','PENDING',now(),now())",
        UUID.randomUUID(),
        institution,
        tutor,
        document);
  }

  private static Flyway migrations(final JdbcTemplate jdbc, final String target) {
    return Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .outOfOrder(true)
        .target(target)
        .load();
  }
}
