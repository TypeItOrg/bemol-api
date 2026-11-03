package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@IntegrationTest
class PersonRequiredFieldsMigrationPostgresIntegrationTest {
  private static final String REQUIRED_FIELDS_MIGRATION = "20261102235222";

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void migratesWithoutChangingPeopleAndRequiresCompleteNewWrites(final boolean upgrade) {
    final var database =
        new PostgresTestDatabase(
            "postgres:18-alpine", upgrade ? "person_fields_upgrade" : "person_fields_fresh");
    final var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    migrations(jdbc, upgrade ? "20261102181343" : REQUIRED_FIELDS_MIGRATION).migrate();
    final UUID institution = new MigrationFixtures(jdbc).firstInstitutionId();
    final UUID country =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT country_id FROM countries WHERE iso_code='ARG'", UUID.class));
    final UUID existing =
        insertPerson(
            jdbc,
            institution,
            "12345678",
            upgrade ? null : country,
            upgrade ? null : "03534100000");
    final var before = jdbc.queryForMap("SELECT * FROM people WHERE person_id=?", existing);

    final var current = migrations(jdbc, REQUIRED_FIELDS_MIGRATION);
    assertThat(current.migrate().migrationsExecuted).isEqualTo(upgrade ? 1 : 0);
    assertThat(current.validateWithResult().validationSuccessful).isTrue();
    assertThat(current.migrate().migrationsExecuted).isZero();
    assertThat(jdbc.queryForMap("SELECT * FROM people WHERE person_id=?", existing))
        .isEqualTo(before);

    final UUID created = insertPerson(jdbc, institution, "22345678", country, "03534100000");
    assertThatThrownBy(() -> insertPerson(jdbc, institution, "32345678", null, "03534100000"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("people_nationality_required");
    for (final String phone : new String[] {null, "", " \t\n"}) {
      assertThatThrownBy(() -> insertPerson(jdbc, institution, "42345678", country, phone))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("people_phone_number_required");
    }
    assertThatThrownBy(
            () -> jdbc.update("UPDATE people SET phone_number=NULL WHERE person_id=?", created))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("people_phone_number_required");
  }

  private UUID insertPerson(
      final JdbcTemplate jdbc,
      final UUID institution,
      final String document,
      final @Nullable UUID country,
      final @Nullable String phone) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO people
          (person_id,institution_id,document_number,first_name,last_name,nationality_country_id,phone_number,deleted,created_at,updated_at)
        VALUES (?, ?, ?, 'Persona', 'Prueba', ?, ?, false, now(), now())
        """,
        id,
        institution,
        document,
        country,
        phone);
    return id;
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
