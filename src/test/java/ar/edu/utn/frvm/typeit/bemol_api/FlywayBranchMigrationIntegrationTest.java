package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@IntegrationTest
class FlywayBranchMigrationIntegrationTest {

  private static final String PREVIOUS_VERSION = "20261011033241";
  private static final Set<String> LATE_VERSIONS =
      Set.of(
          "20260924210147", "20260930174829", "20260930185419", "20260930195831", "20260930201516");

  @TempDir Path previousRelease;

  @Test
  void shouldBackfillCourseReadDependenciesWithoutGrantingThemToOtherRoles() {
    final var database =
        new PostgresTestDatabase("postgres:18-alpine", "course_permission_upgrade");
    final var jdbc = jdbc(database);
    Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .target("20261021223422")
        .load()
        .migrate();
    final var fixtures = new MigrationFixtures(jdbc);
    final UUID institution = fixtures.firstInstitutionId(),
        reader = UUID.randomUUID(),
        unrelated = UUID.randomUUID();
    for (final UUID role : List.of(reader, unrelated)) {
      jdbc.update(
          "INSERT INTO roles (role_id, institution_id, scope, code, name, is_system, created_at,"
              + " updated_at) VALUES (?, ?, 'INSTITUTION', ?, ?, false, CURRENT_TIMESTAMP,"
              + " CURRENT_TIMESTAMP)",
          role,
          institution,
          "UPGRADE_" + role,
          "Upgrade " + role);
    }
    for (final String permission :
        List.of(
            "institution:enrollment-application:read",
            "institution:enrollment-application-course:read")) {
      jdbc.update(
          "INSERT INTO permissions (permission_id, code, description, scope, created_at,"
              + " updated_at) VALUES (?, ?, ?, 'INSTITUTION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
              + " ON CONFLICT (code) DO NOTHING",
          UUID.randomUUID(),
          permission,
          permission);
    }
    jdbc.update(
        "INSERT INTO role_permissions (role_id, permission_id) SELECT ?, permission_id FROM"
            + " permissions WHERE code = 'institution:enrollment-application-course:read'",
        reader);

    final var upgrade =
        Flyway.configure()
            .dataSource(requireNonNull(jdbc.getDataSource()))
            .locations("classpath:db/migration", "classpath:db/dev")
            .sqlMigrationPrefix("")
            .load();
    assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.permission_id ="
                    + " rp.permission_id WHERE rp.role_id = ? AND p.code ="
                    + " 'institution:enrollment-application:read'",
                Long.class,
                reader))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id = ?", Long.class, unrelated))
        .isZero();
    assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
    assertThat(upgrade.migrate().migrationsExecuted).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"dev", "staging", "prod"})
  @DisplayName("Should enable late migrations in every profile without disabling validation")
  void shouldKeepSharedPolicyAcrossProfiles(final String profile) throws IOException {
    final var settings = settings(profile);

    assertThat(settings.isOutOfOrder()).isTrue();
    assertThat(settings.isValidateOnMigrate()).isTrue();
    assertThat(settings.isValidateMigrationNaming()).isTrue();
    assertThat(settings.getIgnoreMigrationPatterns()).doesNotContain("*:ignored", "*:pending");
  }

  @Test
  @DisplayName("Should upgrade the previous staging history without resetting existing data")
  void shouldUpgradePreviousStagingHistory() throws IOException {
    final var database = new PostgresTestDatabase("postgres:18-alpine", "flyway_branch_upgrade");
    final var jdbc = jdbc(database);
    copyPreviousRelease();
    Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations("filesystem:" + previousRelease)
        .sqlMigrationPrefix("")
        .load()
        .migrate();

    final var history =
        jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    assertThat(history).hasSize(79);
    assertThat(history.getLast().get("version")).isEqualTo(PREVIOUS_VERSION);
    final var fixtures = new MigrationFixtures(jdbc);
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID tutorId = UUID.randomUUID();
    final UUID dependentId = UUID.randomUUID();
    final UUID studentId = UUID.randomUUID();
    final UUID profileId = UUID.randomUUID();
    fixtures.insertPerson(tutorId, institutionId, "70000001", false);
    fixtures.insertPerson(dependentId, institutionId, "70000002", false);
    jdbc.update(
        """
        INSERT INTO roles (
          role_id, institution_id, scope, code, name, is_system, created_at, updated_at
        ) VALUES (?, ?, 'INSTITUTION', 'APPLICANT', 'Applicant', true,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        institutionId);
    jdbc.update(
        """
        INSERT INTO students (
          student_id, institution_id, person_id, enrollment_date, status, created_at, updated_at
        ) VALUES (?, ?, ?, DATE '2026-03-01', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        studentId,
        institutionId,
        dependentId);
    jdbc.update(
        """
        INSERT INTO guardian_profiles (
          guardian_profile_id, institution_id, person_id, created_at, updated_at
        ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        profileId,
        institutionId,
        tutorId);
    jdbc.update(
        """
        INSERT INTO student_guardians (
          student_guardian_id, institution_id, guardian_profile_id, student_id,
          relationship, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'FATHER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        institutionId,
        profileId,
        studentId);

    final var flyway = currentRelease(jdbc);
    assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(LATE_VERSIONS.size());

    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(flyway.info().all())
        .filteredOn(migration -> migration.getState() == MigrationState.OUT_OF_ORDER)
        .extracting(migration -> requireNonNull(migration.getVersion()).getVersion())
        .containsExactlyInAnyOrderElementsOf(LATE_VERSIONS);
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM flyway_schema_history WHERE installed_rank <= 79 ORDER BY"
                    + " installed_rank"))
        .isEqualTo(history);
    assertThat(
            jdbc.queryForList(
                "SELECT person_id FROM people WHERE person_id IN (?, ?) ORDER BY document_number",
                UUID.class,
                tutorId,
                dependentId))
        .containsExactly(tutorId, dependentId);
    assertThat(
            jdbc.queryForObject(
                "SELECT person_id FROM students WHERE student_id = ?", UUID.class, studentId))
        .isEqualTo(dependentId);
    assertThat(
            jdbc.queryForMap(
                "SELECT relationship, status FROM person_guardians WHERE institution_id = ? AND"
                    + " tutor_person_id = ? AND dependent_person_id = ?",
                institutionId,
                tutorId,
                dependentId))
        .containsEntry("relationship", "FATHER")
        .containsEntry("status", "ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT r.code FROM person_role_assignments a JOIN roles r ON r.role_id = a.role_id"
                    + " WHERE a.person_id = ? AND a.institution_id = ?",
                String.class,
                dependentId,
                institutionId))
        .isEqualTo("APPLICANT");

    final var freshDatabase = new PostgresTestDatabase("postgres:18-alpine", "flyway_branch_fresh");
    final var freshJdbc = jdbc(freshDatabase);
    final var freshFlyway = currentRelease(freshJdbc);
    freshFlyway.migrate();
    assertThat(freshFlyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(columns(jdbc)).isEqualTo(columns(freshJdbc));
    assertThat(flyway.migrate().migrationsExecuted).isZero();

    // Corrupt only this disposable test database to verify validation still blocks startup.
    jdbc.update(
        "UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = ?",
        PREVIOUS_VERSION);
    assertThatThrownBy(flyway::migrate)
        .isInstanceOf(FlywayValidateException.class)
        .hasMessageContaining("checksum mismatch");
  }

  private void copyPreviousRelease() throws IOException {
    final var resolver = new PathMatchingResourcePatternResolver();
    for (final String location : List.of("migration", "dev")) {
      for (final var resource : resolver.getResources("classpath:db/" + location + "/*.sql")) {
        final String filename = requireNonNull(resource.getFilename());
        final String version = filename.substring(0, filename.indexOf("__"));
        // Reproduce ed4ecbd: these five scripts only arrived with the next release.
        if (version.compareTo(PREVIOUS_VERSION) <= 0 && !LATE_VERSIONS.contains(version)) {
          try (final var input = resource.getInputStream()) {
            Files.copy(input, previousRelease.resolve(filename));
          }
        }
      }
    }
  }

  private static Flyway currentRelease(final JdbcTemplate jdbc) throws IOException {
    final var settings = settings("staging");
    return Flyway.configure()
        .dataSource(requireNonNull(jdbc.getDataSource()))
        .locations(settings.getLocations().toArray(String[]::new))
        .sqlMigrationPrefix(settings.getSqlMigrationPrefix())
        .validateMigrationNaming(settings.isValidateMigrationNaming())
        .validateOnMigrate(settings.isValidateOnMigrate())
        .outOfOrder(settings.isOutOfOrder())
        .load();
  }

  private static FlywayProperties settings(final String profile) throws IOException {
    // Test resources shadow application.properties; read the actual deployment configuration.
    final Properties properties =
        PropertiesLoaderUtils.loadProperties(
            new FileSystemResource("src/main/resources/application.properties"));
    properties.putAll(
        PropertiesLoaderUtils.loadProperties(
            new FileSystemResource("src/main/resources/application-" + profile + ".properties")));
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("spring.flyway", Bindable.of(FlywayProperties.class))
        .get();
  }

  private static JdbcTemplate jdbc(final PostgresTestDatabase database) {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            database.getJdbcUrl(), database.getUsername(), database.getPassword()));
  }

  private static List<Map<String, @Nullable Object>> columns(final JdbcTemplate jdbc) {
    return jdbc.queryForList(
        """
        SELECT table_name, column_name, data_type, is_nullable, column_default
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
        ORDER BY table_name, column_name
        """);
  }
}
