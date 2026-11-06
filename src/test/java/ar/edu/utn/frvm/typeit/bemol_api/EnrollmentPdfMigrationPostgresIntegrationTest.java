package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

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
class EnrollmentPdfMigrationPostgresIntegrationTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void supportsFreshInstallAndUpgradeWithoutBackfill(final boolean upgrade) {
    final var database =
        new PostgresTestDatabase("postgres:18-alpine", upgrade ? "pdf_upgrade" : "pdf_fresh");
    final var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    migrations(jdbc, upgrade ? "20261105173842" : "20261105175029").migrate();
    final UUID institution = new MigrationFixtures(jdbc).firstInstitutionId();
    final var before =
        jdbc.queryForMap("SELECT * FROM institutions WHERE institution_id=?", institution);
    final var current = migrations(jdbc, "20261105175029");
    assertThat(current.migrate().migrationsExecuted).isEqualTo(upgrade ? 2 : 0);
    assertThat(current.validateWithResult().validationSuccessful).isTrue();
    assertThat(current.migrate().migrationsExecuted).isZero();
    assertThat(jdbc.queryForMap("SELECT * FROM institutions WHERE institution_id=?", institution))
        .isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment_pdf_settings", Long.class))
        .isZero();
    jdbc.update(
        "INSERT INTO enrollment_pdf_settings(institution_id,title,show_applicant_signature,show_responsible_signature) VALUES (?,'Título',false,true)",
        institution);
    assertThat(
            jdbc.queryForObject(
                "SELECT show_applicant_signature FROM enrollment_pdf_settings WHERE institution_id=?",
                Boolean.class,
                institution))
        .isFalse();
    for (final var title : new String[] {"", " \t\n", "x".repeat(151)}) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE enrollment_pdf_settings SET title=? WHERE institution_id=?",
                      title,
                      institution))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("enrollment_pdf_settings_title_check");
    }
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE enrollment_pdf_settings SET header_text=? WHERE institution_id=?",
                    "x".repeat(501),
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("enrollment_pdf_settings_header_check");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE enrollment_pdf_settings SET introduction_text=? WHERE institution_id=?",
                    "x".repeat(1001),
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("enrollment_pdf_settings_introduction_check");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE enrollment_pdf_settings SET footer_text=? WHERE institution_id=?",
                    "x".repeat(501),
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("enrollment_pdf_settings_footer_check");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO enrollment_pdf_settings(institution_id,title,show_applicant_signature,show_responsible_signature) VALUES (?,'Título',true,true)",
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO enrollment_pdf_settings(institution_id,title,show_applicant_signature,show_responsible_signature) VALUES (?,'Título',true,true)",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE enrollment_pdf_settings SET show_responsible_signature=NULL WHERE institution_id=?",
                    institution))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Flyway migrations(final JdbcTemplate jdbc, final String target) {
    return Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .outOfOrder(true)
        .target(target)
        .load();
  }
}
