package ar.edu.utn.frvm.typeit.bemol_api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@IntegrationTest
class EmailVerificationMigrationPostgresIntegrationTest {
  @Test
  void preservesLegacyAccountsAndDefaultsNewAccountsWithoutRequiringEmailVerification() {
    final var database =
        new PostgresTestDatabase("postgres:18-alpine", "email_verification_upgrade");
    final var dataSource =
        new DriverManagerDataSource(
            database.getJdbcUrl(), database.getUsername(), database.getPassword());
    final var jdbc = new JdbcTemplate(dataSource);
    migrations(dataSource, "20260912032354").migrate();

    final var fixtures = new MigrationFixtures(jdbc);
    final UUID institution = fixtures.firstInstitutionId();
    final UUID legacyPerson = UUID.randomUUID(), newPerson = UUID.randomUUID();
    final UUID legacyUser = UUID.randomUUID(), newUser = UUID.randomUUID();
    fixtures.insertPerson(legacyPerson, institution, "99112201", false);
    fixtures.insertPerson(newPerson, institution, "99112202", false);
    insertUser(jdbc, legacyUser, legacyPerson, institution);

    migrations(dataSource, "20260912045658").migrate();
    insertUser(jdbc, newUser, newPerson, institution);

    for (final UUID user : new UUID[] {legacyUser, newUser}) {
      assertThat(
              jdbc.queryForMap(
                  "SELECT email_verification_status, email_verified_at, enabled, password FROM users WHERE user_id = ?",
                  user))
          .containsEntry("email_verification_status", "NOT_REQUIRED")
          .containsEntry("email_verified_at", null)
          .containsEntry("enabled", true)
          .containsEntry("password", "encoded-password");
    }
  }

  private void insertUser(
      final JdbcTemplate jdbc, final UUID id, final UUID person, final UUID institution) {
    jdbc.update(
        """
        INSERT INTO users (
          user_id, person_id, institution_id, password, enabled, created_at, updated_at
        ) VALUES (?, ?, ?, 'encoded-password', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        person,
        institution);
  }

  private Flyway migrations(final DataSource dataSource, final String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .outOfOrder(true)
        .target(target)
        .load();
  }
}
