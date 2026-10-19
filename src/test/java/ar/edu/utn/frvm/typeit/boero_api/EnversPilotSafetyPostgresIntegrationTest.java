package ar.edu.utn.frvm.typeit.boero_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateShiftRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.UpdateShiftRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreateShiftUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateShiftUseCase;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.GlobalAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import java.io.IOException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.integration.envers.enabled=false")
class EnversPilotSafetyPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String PREVIOUS_VERSION = "20261018172641";
  private static final String PILOT_VERSION = "20261018200200";
  private static final PostgresTestDatabase DATABASE =
      new PostgresTestDatabase("postgres:18-alpine", "envers_pilot_disabled");

  @DynamicPropertySource
  static void pilotDatabaseProperties(final DynamicPropertyRegistry registry) {
    // Configure the pool without competing with the inherited shared datasource URL.
    registry.add("spring.datasource.hikari.jdbc-url", DATABASE::getJdbcUrl);
  }

  @Autowired Environment environment;
  @Autowired GlobalAuditEventsUseCase audit;
  @Autowired CreateShiftUseCase createShift;
  @Autowired UpdateShiftUseCase updateShift;
  @MockitoBean MailSender mailSender;

  @Test
  void supportsExplicitDisableAndUpgradesExistingDataWithoutBackfillOrChangingGlobalAudit()
      throws IOException {
    assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
        .isEqualTo("envers_pilot_disabled");
    assertThat(
            environment.getProperty("spring.jpa.properties.hibernate.integration.envers.enabled"))
        .isEqualTo("false");
    final var mainSettings =
        PropertiesLoaderUtils.loadProperties(
            new FileSystemResource("src/main/resources/application.properties"));
    assertThat(
            mainSettings.getProperty("spring.jpa.properties.hibernate.integration.envers.enabled"))
        .isEqualTo("true");
    assertThat(
            mainSettings.getProperty(
                "spring.jpa.properties.org.hibernate.envers.audit_table_prefix"))
        .isEqualTo(
            environment.getProperty(
                "spring.jpa.properties.org.hibernate.envers.audit_table_prefix"));
    authenticatePlatformAdministrator();
    final UUID institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    final UUID shift =
        createShift.execute(institution, new CreateShiftRequest("Turno sin piloto", null)).id();
    updateShift.execute(institution, shift, new UpdateShiftRequest("Turno actualizado", null));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM envers_poc_shifts_aud WHERE shift_id = ?", Long.class, shift))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE entity_id = ?", Long.class, shift))
        .isEqualTo(2);

    final UUID disabledEvent =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT audit_event_id FROM audit_events WHERE entity_id = ? ORDER BY occurred_at DESC, audit_event_id DESC LIMIT 1",
                UUID.class,
                shift));
    assertThat(audit.get(disabledEvent).history()).isNull();

    final var database = new PostgresTestDatabase("postgres:18-alpine", "envers_pilot_upgrade");
    final var source =
        new DriverManagerDataSource(
            database.getJdbcUrl(), database.getUsername(), database.getPassword());
    final var jdbc = new JdbcTemplate(source);
    Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/migration", "classpath:db/dev")
        .sqlMigrationPrefix("")
        .outOfOrder(true)
        .validateMigrationNaming(true)
        .target(PREVIOUS_VERSION)
        .load()
        .migrate();
    final var previous = new MigrationFixtures(jdbc);
    final UUID existingInstitution = previous.firstInstitutionId(),
        existingShift = UUID.randomUUID();
    previous.insertShift(existingShift, existingInstitution, "Dato previo", true);
    final UUID event = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO audit_events (audit_event_id, institution_id, actor_kind, action, entity_type, entity_id, acted_on_behalf, occurred_at, details)
        VALUES (?, ?, 'ANONYMOUS', 'SHIFT_CREATED', 'SHIFT', ?, false, CURRENT_TIMESTAMP, '{"preserved": true}'::jsonb)
        """,
        event,
        existingInstitution,
        existingShift);
    final var original = jdbc.queryForMap("SELECT * FROM shifts WHERE shift_id = ?", existingShift);
    final var originalAudit =
        jdbc.queryForMap("SELECT * FROM audit_events WHERE audit_event_id = ?", event);
    final var history =
        jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    final var upgrade =
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration", "classpath:db/dev")
            .sqlMigrationPrefix("")
            .outOfOrder(true)
            .validateMigrationNaming(true)
            .target(PILOT_VERSION)
            .load();
    assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
    upgrade.validate();
    assertThat(jdbc.queryForMap("SELECT * FROM shifts WHERE shift_id = ?", existingShift))
        .isEqualTo(original);
    assertThat(jdbc.queryForMap("SELECT * FROM audit_events WHERE audit_event_id = ?", event))
        .isEqualTo(originalAudit);
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM flyway_schema_history WHERE version <> ? ORDER BY installed_rank",
                PILOT_VERSION))
        .isEqualTo(history);
    final var tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'envers_poc_%'",
            String.class);
    assertThat(tables).hasSize(7);
    for (final var table : tables) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + requireNonNull(table), Long.class))
          .isZero();
    }

    jdbc.update(
        "INSERT INTO envers_poc_revisions(id, occurred_at_epoch_ms, actor_kind) VALUES (100, 1, 'ANONYMOUS')");
    jdbc.update(
        "INSERT INTO envers_poc_shifts_aud(shift_id, rev, revtype, institution_id, name, active) VALUES (?, 100, 0, ?, 'Dato previo', true)",
        existingShift,
        existingInstitution);
    final var pilotHistory = jdbc.queryForList("SELECT * FROM envers_poc_shifts_aud");
    final var beforeExpansion =
        jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    final var expansion =
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration", "classpath:db/dev")
            .sqlMigrationPrefix("")
            .outOfOrder(true)
            .validateMigrationNaming(true)
            .target("20261018205721")
            .load();
    assertThat(expansion.migrate().migrationsExecuted).isEqualTo(1);
    expansion.validate();
    assertThat(jdbc.queryForList("SELECT * FROM envers_poc_shifts_aud")).isEqualTo(pilotHistory);
    assertThat(jdbc.queryForMap("SELECT * FROM shifts WHERE shift_id = ?", existingShift))
        .isEqualTo(original);
    assertThat(jdbc.queryForMap("SELECT * FROM audit_events WHERE audit_event_id = ?", event))
        .isEqualTo(originalAudit);
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM flyway_schema_history WHERE version <> '20261018205721' ORDER BY installed_rank"))
        .isEqualTo(beforeExpansion);
    final var beforeCorrelation = jdbc.queryForList("SELECT * FROM envers_poc_revisions");
    final var correlation =
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration", "classpath:db/dev")
            .sqlMigrationPrefix("")
            .outOfOrder(true)
            .validateMigrationNaming(true)
            .target("20261018212657")
            .load();
    assertThat(correlation.migrate().migrationsExecuted).isEqualTo(1);
    correlation.validate();
    assertThat(jdbc.queryForMap("SELECT * FROM audit_events WHERE audit_event_id = ?", event))
        .containsAllEntriesOf(originalAudit)
        .containsEntry("transaction_id", null);
    assertThat(
            jdbc.queryForList(
                "SELECT id, occurred_at_epoch_ms, actor_kind, actor_person_id, actor_platform_account_id, request_id FROM envers_poc_revisions"))
        .isEqualTo(beforeCorrelation);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM envers_poc_revisions WHERE transaction_id IS NOT NULL",
                Long.class))
        .isZero();
    final var expandedTables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'envers_poc_%'",
            String.class);
    assertThat(expandedTables).hasSize(44);
    for (final var table : expandedTables) {
      if (!tables.contains(table)) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + requireNonNull(table), Long.class))
            .isZero();
      }
    }
  }
}
