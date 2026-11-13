package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@IntegrationTest
class PlatformAccountEmailConstraintsPostgresIntegrationTest {

  @Test
  void enforcesEmailChangeConstraints() {
    final var database = new PostgresTestDatabase("postgres:18-alpine", "platform_email_fresh");
    final var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    migrations(jdbc, "latest").migrate();
    final UUID first = insertAccount(jdbc, "before@example.test");
    final var before =
        jdbc.queryForMap("SELECT * FROM platform_accounts WHERE platform_account_id=?", first);

    final var current = migrations(jdbc, "latest");
    assertThat(current.validateWithResult().validationSuccessful).isTrue();
    assertThat(current.migrate().migrationsExecuted).isZero();
    assertThat(
            jdbc.queryForMap("SELECT * FROM platform_accounts WHERE platform_account_id=?", first))
        .isEqualTo(before);

    final UUID second = insertAccount(jdbc, "second@example.test");
    insertToken(jdbc, first, "a".repeat(64));
    assertThatThrownBy(() -> insertToken(jdbc, first, "b".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("platform_email_change_account_unique");
    assertThatThrownBy(() -> insertToken(jdbc, second, "a".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("platform_email_change_token_unique");
    assertThatThrownBy(() -> insertToken(jdbc, UUID.randomUUID(), "c".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("platform_email_change_account_fk");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE platform_email_change_tokens SET expires_at=issued_at"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("platform_email_change_expiry_check");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE platform_email_change_tokens SET new_email=previous_email"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("platform_email_change_email_check");
  }

  private UUID insertAccount(final JdbcTemplate jdbc, final String email) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO platform_accounts
          (platform_account_id,email,first_name,last_name,password,enabled,created_at,updated_at)
        VALUES (?,?,'Prueba','Cuenta','unchanged-hash',true,now(),now())
        """,
        id,
        email);
    return id;
  }

  private void insertToken(final JdbcTemplate jdbc, final UUID account, final String hash) {
    jdbc.update(
        """
        INSERT INTO platform_email_change_tokens
          (id,platform_account_id,previous_email,new_email,password_fingerprint,token_hash,issued_at,expires_at)
        VALUES (?,?,'before@example.test','new@example.test',?,?,now(),now()+interval '24 hours')
        """,
        UUID.randomUUID(),
        account,
        "f".repeat(64),
        hash);
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
