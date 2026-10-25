package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@IntegrationTest
class WebpMigrationPostgresIntegrationTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void supportsFreshInstallAndUpgradeWithoutChangingExistingFormats(final boolean upgrade) {
    final var database =
        new PostgresTestDatabase("postgres:18-alpine", upgrade ? "webp_upgrade" : "webp_fresh");
    final var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    migrations(
            jdbc,
            upgrade ? MigrationVersion.fromVersion("20261025181740") : MigrationVersion.LATEST)
        .migrate();

    final var institution = new MigrationFixtures(jdbc).firstInstitutionId();
    final var document = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO document_definitions
          (id, institution_id, name, instructions, allowed_formats, active)
        VALUES (?, ?, 'Existing document', '', '["image/jpeg","image/png"]'::jsonb, true)
        """,
        document,
        institution);
    jdbc.update(
        """
        UPDATE institutions SET logo_key = 'existing-logo', logo_content_type = 'image/png',
          logo_version = 'existing-version', logo_size = 64 WHERE institution_id = ?
        """,
        institution);

    final var current = migrations(jdbc, MigrationVersion.LATEST);
    current.migrate();
    assertThat(current.validateWithResult().validationSuccessful).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT allowed_formats::text FROM document_definitions WHERE id = ?",
                String.class,
                document))
        .isEqualTo("[\"image/jpeg\", \"image/png\"]");
    assertThat(
            jdbc.queryForObject(
                "SELECT logo_content_type FROM institutions WHERE institution_id = ?",
                String.class,
                institution))
        .isEqualTo("image/png");

    jdbc.update(
        "UPDATE institutions SET logo_content_type = 'image/webp' WHERE institution_id = ?",
        institution);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE institutions SET logo_content_type = 'image/svg+xml' WHERE institution_id = ?",
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("institutions_logo_metadata_check");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE institutions SET logo_size = 2097153 WHERE institution_id = ?",
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbc.update(
        "UPDATE document_definitions SET allowed_formats = '[\"image/webp\"]'::jsonb WHERE id = ?",
        document);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE document_definitions SET allowed_formats = '[]'::jsonb WHERE id = ?",
                    document))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE document_definitions SET allowed_formats = '[\"image/svg+xml\"]'::jsonb WHERE id = ?",
                    document))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Copy the migrated CHECK constraints without requiring an unrelated enrollment workflow.
    jdbc.execute(
        "CREATE TABLE webp_requirement_checks (LIKE enrollment_document_requirements INCLUDING DEFAULTS INCLUDING CONSTRAINTS)");
    jdbc.update(
        """
        INSERT INTO webp_requirement_checks
          (id, application_id, source_requirement_id, institution_id, document_id, name, instructions, level, allowed_formats, display_order)
        VALUES (?, ?, ?, ?, ?, 'WebP document', '', 'OPTIONAL', '["image/webp"]'::jsonb, 0)
        """,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        institution,
        document);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE webp_requirement_checks SET allowed_formats = '[\"image/svg+xml\"]'::jsonb"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE webp_requirement_checks SET allowed_formats = '[]'::jsonb"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static Flyway migrations(final JdbcTemplate jdbc, final MigrationVersion target) {
    return Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .outOfOrder(true)
        .target(target)
        .load();
  }
}
