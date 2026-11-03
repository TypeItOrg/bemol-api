package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformPasskeyRequiredException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyManagementUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PlatformPasskeyPersistenceIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired private PlatformAccountRepository accounts;
  @Autowired private PlatformPasskeyManagementUseCase passkeys;
  @Autowired private PlatformPasskeyStateStore state;
  @Autowired private JdbcTemplate database;
  @Autowired private JsonMapper json;

  @Test
  void auditKeepsDisplayNamesForRenameAndRevocationWithoutCredentialData() {
    final var account = createAccount();
    final UUID key = insertKey(account.getId(), UUID.randomUUID().toString());
    insertKey(account.getId(), UUID.randomUUID().toString());
    final var principal = AuthTestData.platformPrincipal(account.getId());
    try {
      passkeys.rename(account.getId(), key, "  Equipo personal  ");
      passkeys.rename(account.getId(), key, "Equipo personal");
      state.grantRecent(account.getId(), principal.sessionId(), "revoke:" + key);
      passkeys.revoke(principal, key);

      final var events =
          database.queryForList(
              "SELECT action, details::text AS details FROM audit_events WHERE entity_id = ?", key);
      assertThat(events).hasSize(2);
      assertThat(events)
          .allSatisfy(
              event -> {
                final boolean renamed = "PASSKEY_UPDATED".equals(event.get("action"));
                assertThat(json.readTree(requireNonNull(event.get("details")).toString()))
                    .isEqualTo(
                        json.valueToTree(
                            Map.of(
                                "entityName",
                                "Equipo personal",
                                "platformAccountId",
                                account.getId().toString(),
                                "changedFields",
                                List.of(renamed ? "label" : "active"),
                                "before",
                                renamed ? Map.of("label", "Prueba") : Map.of("active", true),
                                "after",
                                renamed
                                    ? Map.of("label", "Equipo personal")
                                    : Map.of("active", false))));
              });
    } finally {
      accounts.deleteById(account.getId());
    }
  }

  @Test
  void postgresEnforcesCredentialOwnershipAndUniqueness() {
    final var account = createAccount();
    try {
      insertKey(account.getId(), "credential");
      assertThatThrownBy(() -> insertKey(account.getId(), "credential"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertKey(UUID.randomUUID(), "orphan"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      accounts.deleteById(account.getId());
    }
  }

  @Test
  void concurrentRevocationPreservesOneActiveKey() throws Exception {
    final var account = createAccount();
    final UUID first = insertKey(account.getId(), UUID.randomUUID().toString());
    final UUID second = insertKey(account.getId(), UUID.randomUUID().toString());
    final var principal = AuthTestData.platformPrincipal(account.getId());
    state.grantRecent(account.getId(), principal.sessionId(), "revoke:" + first);
    state.grantRecent(account.getId(), principal.sessionId(), "revoke:" + second);
    final var start = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var tasks =
          java.util.List.of(first, second).stream()
              .map(
                  id ->
                      executor.submit(
                          () -> {
                            start.await();
                            try {
                              passkeys.revoke(principal, id);
                              return "revoked";
                            } catch (PlatformPasskeyRequiredException exception) {
                              return "preserved";
                            }
                          }))
              .toList();
      start.countDown();
      assertThat(
              java.util.List.of(
                  tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("revoked", "preserved");
      assertThat(
              database.queryForObject(
                  "SELECT count(*) FROM platform_passkey_credentials WHERE platform_account_id = ? AND revoked_at IS NULL",
                  Long.class,
                  account.getId()))
          .isEqualTo(1L);
    } finally {
      accounts.deleteById(account.getId());
    }
  }

  private PlatformAccount createAccount() {
    return accounts.saveAndFlush(
        PlatformAccount.builder()
            .email(UUID.randomUUID() + "@example.test")
            .name("Prueba")
            .lastName("Plataforma")
            .password("test-password-hash")
            .enabled(true)
            .build());
  }

  private UUID insertKey(final UUID accountId, final String externalId) {
    final UUID id = UUID.randomUUID();
    database.update(
        """
        INSERT INTO platform_passkey_credentials
        (platform_passkey_credential_id, platform_account_id, credential_id, public_key_cose, user_handle, label, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 'Prueba', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        accountId,
        externalId,
        new byte[] {1},
        new byte[] {2});
    return id;
  }
}
