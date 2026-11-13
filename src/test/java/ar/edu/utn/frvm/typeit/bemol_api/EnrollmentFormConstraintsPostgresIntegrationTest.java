package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class EnrollmentFormConstraintsPostgresIntegrationTest {
  @Test
  void enforcesVersionAndAssignmentBoundaries() {
    final var jdbc = database("form_constraints");
    migrations(jdbc).migrate();
    final var context = context(jdbc);
    final UUID baseApplication = application(jdbc, context, "DRAFT", null);

    verifyVersionsAndAssignments(jdbc, context, baseApplication);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void assignmentAndArchivalSerializeBothDirections(final boolean archiveFirst) throws Exception {
    final var jdbc = database(archiveFirst ? "form_archive_race" : "form_assignment_race");
    migrations(jdbc).migrate();
    final var context = context(jdbc);
    final UUID version = draft(jdbc, context.institution(), form(jdbc, context.institution()), 1);
    publish(jdbc, version);
    final var transaction =
        new TransactionTemplate(
            new DataSourceTransactionManager(requireNonNull(jdbc.getDataSource())));
    final var started = new CountDownLatch(1);

    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var waiting =
          requireNonNull(
              transaction.execute(
                  status -> {
                    if (archiveFirst) {
                      archive(jdbc, version);
                    } else {
                      assignment(jdbc, context, version);
                    }
                    final var future =
                        executor.submit(
                            () -> {
                              started.countDown();
                              if (archiveFirst) {
                                violates(
                                    () -> assignment(jdbc, context, version),
                                    "enrollment_form_assignments_published_check");
                              } else {
                                violates(
                                    () -> archive(jdbc, version),
                                    "enrollment_form_versions_assigned_check");
                              }
                            });
                    try {
                      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                      assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
                          .isInstanceOf(TimeoutException.class);
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException(exception);
                    }
                    return future;
                  }));
      waiting.get(10, TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM enrollment_form_versions WHERE id = ?", String.class, version))
        .isEqualTo(archiveFirst ? "ARCHIVED" : "PUBLISHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM enrollment_form_assignments WHERE form_version_id = ?",
                Long.class,
                version))
        .isEqualTo(archiveFirst ? 0 : 1);
  }

  private void verifyVersionsAndAssignments(
      final JdbcTemplate jdbc, final Context context, final UUID baseApplication) {
    final UUID form = form(jdbc, context.institution());
    final UUID version = draft(jdbc, context.institution(), form, 1);
    violates(
        () -> draft(jdbc, context.institution(), form, 2), "enrollment_form_versions_draft_unique");
    violates(
        () -> assignment(jdbc, context, version), "enrollment_form_assignments_published_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_form_versions SET definition = '[]'::jsonb WHERE id = ?",
                version),
        "enrollment_form_versions_definition_check");
    publish(jdbc, version);
    final UUID secondVersion = draft(jdbc, context.institution(), form, 2);
    publish(jdbc, secondVersion);
    violates(
        () -> draft(jdbc, context.institution(), form, 2),
        "enrollment_form_versions_number_unique");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_form_versions SET definition = '{\"changed\":true}'::jsonb WHERE id = ?",
                version),
        "enrollment_form_versions_immutable_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_form_versions SET status = 'DRAFT', published_at = NULL WHERE id = ?",
                version),
        "enrollment_form_versions_immutable_check");
    violates(
        () -> jdbc.update("DELETE FROM enrollment_form_versions WHERE id = ?", version),
        "enrollment_form_versions_immutable_check");

    final var other = context(jdbc);
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_forms SET institution_id = ? WHERE id = ?",
                other.institution(),
                form),
        "enrollment_forms_identity_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_form_versions SET version_number = 4 WHERE id = ?", version),
        "enrollment_form_versions_identity_check");
    violates(
        () -> draft(jdbc, other.institution(), form, 3), "enrollment_form_versions_form_tenant_fk");
    violates(() -> assignment(jdbc, other, version), "enrollment_form_assignments_published_check");
    violates(
        () ->
            assignment(
                jdbc, new Context(context.institution(), other.path(), context.year()), version),
        "enrollment_form_assignments_path_tenant_fk");
    violates(
        () ->
            assignment(
                jdbc, new Context(context.institution(), context.path(), other.year()), version),
        "enrollment_form_assignments_year_tenant_fk");
    final UUID assignment = assignment(jdbc, context, version);
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_form_assignments SET training_path_id = ? WHERE id = ?",
                other.path(),
                assignment),
        "enrollment_form_assignments_identity_check");
    violates(
        () -> assignment(jdbc, context, secondVersion),
        "enrollment_form_assignments_context_unique");
    violates(() -> archive(jdbc, version), "enrollment_form_versions_assigned_check");

    final UUID application = application(jdbc, context, "DRAFT", version);
    jdbc.update(
        "UPDATE enrollment_applications SET custom_answers = '{\"question\":false}'::jsonb, draft_revision = 1 WHERE enrollment_application_id = ?",
        application);
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_applications SET form_version_id = ? WHERE enrollment_application_id = ?",
                secondVersion,
                application),
        "enrollment_application_form_immutable_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_applications SET form_version_id = ? WHERE enrollment_application_id = ?",
                version,
                baseApplication),
        "enrollment_application_form_immutable_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_applications SET custom_answers = '{\"question\":true}'::jsonb WHERE enrollment_application_id = ?",
                baseApplication),
        "enrollment_application_base_answers_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_applications SET training_path_id = ? WHERE enrollment_application_id = ?",
                other.path(),
                application),
        "enrollment_application_form_context_check");
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_applications SET custom_answers = '[]'::jsonb WHERE enrollment_application_id = ?",
                application),
        "enrollment_application_custom_answers_check");
    jdbc.update(
        "UPDATE enrollment_applications SET status = 'SUBMITTED' WHERE enrollment_application_id = ?",
        application);
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_applications SET custom_answers = '{}'::jsonb WHERE enrollment_application_id = ?",
                application),
        "enrollment_application_answers_frozen_check");
    jdbc.update(
        "UPDATE enrollment_form_assignments SET form_version_id = ? WHERE id = ?",
        secondVersion,
        assignment);
    archive(jdbc, version);
    violates(
        () ->
            jdbc.update(
                "UPDATE enrollment_form_versions SET status = 'PUBLISHED', archived_at = NULL WHERE id = ?",
                version),
        "enrollment_form_versions_immutable_check");
    assertThat(
            jdbc.queryForObject(
                "SELECT custom_answers::text FROM enrollment_applications WHERE enrollment_application_id = ?",
                String.class,
                application))
        .isEqualTo("{\"question\": false}");
    violates(
        () -> assignment(jdbc, context, version), "enrollment_form_assignments_published_check");
    violates(
        () -> application(jdbc, context, "DRAFT", version),
        "enrollment_application_form_published_check");
  }

  private static void violates(final Runnable operation, final String constraint) {
    final String sqlState =
        constraint.endsWith("_unique") ? "23505" : constraint.endsWith("_fk") ? "23503" : "23514";
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            exception ->
                assertThat(exception.getMostSpecificCause())
                    .isInstanceOfSatisfying(
                        SQLException.class,
                        sql -> {
                          assertThat(sql.getSQLState()).isEqualTo(sqlState);
                          assertThat(sql.getMessage())
                              .contains("constraint \"" + constraint + "\"");
                        }));
  }

  private static JdbcTemplate database(final String name) {
    final var database = new PostgresTestDatabase("postgres:18-alpine", name);
    return new JdbcTemplate(
        new DriverManagerDataSource(
            database.getJdbcUrl(), database.getUsername(), database.getPassword()));
  }

  private static Flyway migrations(final JdbcTemplate jdbc) {
    return Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .outOfOrder(true)
        .load();
  }

  private static Context context(final JdbcTemplate jdbc) {
    final var fixtures = new MigrationFixtures(jdbc);
    final var context = new Context(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    fixtures.insertTestInstitution(context.institution());
    fixtures.insertTrainingPath(context.path(), context.institution());
    fixtures.insertAcademicYear(context.year(), context.institution(), 2031);
    return context;
  }

  private static UUID form(final JdbcTemplate jdbc, final UUID institution) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO enrollment_forms (id, institution_id, name, created_at, updated_at)
        VALUES (?, ?, 'Formulario', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institution);
    return id;
  }

  private static UUID draft(
      final JdbcTemplate jdbc, final UUID institution, final UUID form, final int number) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO enrollment_form_versions (
          id, institution_id, form_id, version_number, status, definition, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'DRAFT', '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        institution,
        form,
        number);
    return id;
  }

  private static void publish(final JdbcTemplate jdbc, final UUID version) {
    jdbc.update(
        "UPDATE enrollment_form_versions SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP WHERE id = ?",
        version);
  }

  private static void archive(final JdbcTemplate jdbc, final UUID version) {
    jdbc.update(
        "UPDATE enrollment_form_versions SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP WHERE id = ?",
        version);
  }

  private static UUID assignment(
      final JdbcTemplate jdbc, final Context context, final UUID version) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO enrollment_form_assignments (
          id, institution_id, training_path_id, academic_year_id, form_version_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        context.institution(),
        context.path(),
        context.year(),
        version);
    return id;
  }

  private static UUID application(
      final JdbcTemplate jdbc,
      final Context context,
      final String status,
      final @Nullable UUID version) {
    final var fixtures = new MigrationFixtures(jdbc);
    final UUID person = UUID.randomUUID();
    fixtures.insertPerson(person, context.institution(), fixtures.randomDocumentNumber(), false);
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO enrollment_applications (
          enrollment_application_id, institution_id, applicant_person_id, training_path_id,
          academic_year_id, status, form_version_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        context.institution(),
        person,
        context.path(),
        context.year(),
        status,
        version);
    return id;
  }

  private record Context(UUID institution, UUID path, UUID year) {}
}
