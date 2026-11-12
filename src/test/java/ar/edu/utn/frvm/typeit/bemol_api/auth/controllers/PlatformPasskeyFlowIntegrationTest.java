package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.CreatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.CreatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PasskeyAuthenticator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
@IntegrationTest
class PlatformPasskeyFlowIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String HOST = "admin.localhost:3000";
  private static final String ORIGIN = "http://" + HOST;
  private static final String PASSWORD = "Test-password123";
  private static final String BASE = "/api/v1/admin/auth";

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JdbcTemplate database;
  @Autowired private CreatePlatformAccountUseCase createAccount;
  @Autowired private PlatformAccountRepository accounts;

  private PlatformAccount account;

  @BeforeEach
  void createOwnAccount() {
    final String email = java.util.UUID.randomUUID() + "@example.test";
    createAccount.execute(
        new CreatePlatformAccountRequest("Prueba", "Plataforma", email, PASSWORD, true));
    account = accounts.findByEmailIgnoreCase(email).orElseThrow();
  }

  @AfterEach
  void removeOwnAccount() {
    if (account == null) {
      return;
    }
    for (final String table :
        new String[] {
          "platform_refresh_tokens",
          "platform_sessions",
          "platform_account_roles",
          "platform_passkey_credentials"
        }) {
      database.update("DELETE FROM " + table + " WHERE platform_account_id = ?", account.getId());
    }
    accounts.deleteById(account.getId());
  }

  @Test
  void firstEnrollmentThenNormalLoginRequiresBothFactorsAndRejectsReplay() throws Exception {
    final var authenticator = new PasskeyAuthenticator();
    enroll(authenticator, "a".repeat(64));
    final var audit =
        database.queryForObject(
            "SELECT details::text FROM audit_events WHERE action = 'PASSKEY_CREATED' AND details->>'platformAccountId' = ?",
            String.class,
            account.getId().toString());
    assertThat(json.readTree(audit))
        .isEqualTo(
            json.valueToTree(
                Map.of(
                    "entityName", "Equipo de prueba",
                    "platformAccountId", account.getId().toString(),
                    "fieldLabels",
                        Map.of("label", "Nombre de la llave de acceso", "active", "Estado"),
                    "changedFields", List.of("label", "active"),
                    "before", Map.of(),
                    "after", Map.of("label", "Equipo de prueba", "active", true))));
    final var attempt = password();
    assertThat(attempt.path("setupRequired").asBoolean()).isFalse();
    final var options =
        request(
            "/passkeys/authentication/options",
            Map.of("loginAttemptId", attempt.path("loginAttemptId").asString()),
            200);
    final var credential =
        authenticator.authenticate(
            options.path("options"), ORIGIN, account.getWebauthnUserHandle(), true);
    final var body =
        Map.of("ceremonyId", options.path("ceremonyId").asString(), "credential", credential);
    final var session = request("/passkeys/authentication/verify", body, 200);
    assertMe(session, 200);
    request("/passkeys/authentication/verify", body, 401);
    assertThat(activeKeys()).isEqualTo(1);
  }

  @Test
  void recoveryReplacesLostKeysRevokesOldSessionsAndAllowsOnlyTheNewKey() throws Exception {
    final var lost = new PasskeyAuthenticator();
    final var previous = enroll(lost, "b".repeat(64));
    assertMe(previous, 200);
    final var replacement = new PasskeyAuthenticator();
    final var current = enroll(replacement, "c".repeat(64));
    assertMe(previous, 401);
    request(
        "/refresh", Map.of("refreshToken", previous.at("/tokens/refreshToken").asString()), 401);
    assertMe(current, 200);
    assertThat(activeKeys()).isEqualTo(1);
    assertThat(
            database.queryForObject(
                "SELECT count(*) FROM platform_passkey_credentials WHERE platform_account_id = ? AND revoked_at IS NOT NULL",
                Long.class,
                account.getId()))
        .isEqualTo(1);
    final var audit =
        database.queryForObject(
            "SELECT details::text FROM audit_events WHERE action = 'PASSKEY_DELETED' AND details->>'platformAccountId' = ?",
            String.class,
            account.getId().toString());
    assertThat(json.readTree(audit).path("entityName").asString()).isEqualTo("Equipo de prueba");
    assertThat(json.readTree(audit).at("/before/active").asBoolean()).isTrue();
    assertThat(json.readTree(audit).at("/after/active").asBoolean()).isFalse();
    assertMe(login(replacement, 200), 200);
    login(lost, 401);
    assertThat(redis.hasKey(grantKey())).isFalse();
  }

  @Test
  void replacedOperatorCodeCannotConsumeTheNewGrantOrMutateCredentials() throws Exception {
    final String oldCode = "d".repeat(64), newCode = "e".repeat(64);
    final var attempt = password();
    issueCode(oldCode);
    final var options = setupOptions(attempt, oldCode);
    final var authenticator = new PasskeyAuthenticator();
    final var credential = authenticator.register(options.path("options"), ORIGIN);
    final var body =
        Map.of("ceremonyId", options.path("ceremonyId").asString(), "credential", credential);
    issueCode(newCode);
    request("/passkeys/setup/verify", body, 401);
    assertThat(activeKeys()).isZero();
    assertThat(redis.opsForValue().get(grantKey())).isEqualTo(JwtService.hashToken(newCode));
    final var retry = setupOptions(password(), newCode);
    final var session =
        request(
            "/passkeys/setup/verify",
            Map.of(
                "ceremonyId",
                retry.path("ceremonyId").asString(),
                "credential",
                authenticator.register(retry.path("options"), ORIGIN)),
            200);
    assertMe(session, 200);
    assertThat(redis.hasKey(grantKey())).isFalse();
    request("/passkeys/setup/verify", body, 401);
    assertThat(activeKeys()).isEqualTo(1);
  }

  private JsonNode enroll(final PasskeyAuthenticator authenticator, final String code)
      throws Exception {
    final var attempt = password();
    issueCode(code);
    final var options = setupOptions(attempt, code);
    return request(
        "/passkeys/setup/verify",
        Map.of(
            "ceremonyId",
            options.path("ceremonyId").asString(),
            "credential",
            authenticator.register(options.path("options"), ORIGIN)),
        200);
  }

  private JsonNode login(final PasskeyAuthenticator authenticator, final int expected)
      throws Exception {
    final var attempt = password();
    final var options =
        request(
            "/passkeys/authentication/options",
            Map.of("loginAttemptId", attempt.path("loginAttemptId").asString()),
            200);
    return request(
        "/passkeys/authentication/verify",
        Map.of(
            "ceremonyId",
            options.path("ceremonyId").asString(),
            "credential",
            authenticator.authenticate(
                options.path("options"), ORIGIN, account.getWebauthnUserHandle(), true)),
        expected);
  }

  private JsonNode password() throws Exception {
    final var result =
        request("/login", Map.of("email", account.getEmail(), "password", PASSWORD), 200);
    assertThat(result.has("tokens")).isFalse();
    return result;
  }

  private JsonNode setupOptions(final JsonNode attempt, final String code) throws Exception {
    return request(
        "/passkeys/setup/options",
        Map.of(
            "loginAttemptId",
            attempt.path("loginAttemptId").asString(),
            "code",
            code,
            "label",
            "Equipo de prueba"),
        200);
  }

  private JsonNode request(final String path, final Object body, final int expected)
      throws Exception {
    return json.readTree(
        mvc.perform(
                post(BASE + path)
                    .header("X-Institutional-Host", HOST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void assertMe(final JsonNode session, final int expected) throws Exception {
    mvc.perform(
            get(BASE + "/me")
                .header("X-Institutional-Host", HOST)
                .header("Authorization", "Bearer " + session.at("/tokens/accessToken").asString()))
        .andExpect(status().is(expected));
  }

  private void issueCode(final String code) {
    redis.opsForValue().set(grantKey(), JwtService.hashToken(code), Duration.ofMinutes(15));
  }

  private String grantKey() {
    return "bemol:platform:setup:" + account.getId();
  }

  private long activeKeys() {
    return requireNonNull(
        database.queryForObject(
            "SELECT count(*) FROM platform_passkey_credentials WHERE platform_account_id = ? AND revoked_at IS NULL",
            Long.class,
            account.getId()));
  }
}
