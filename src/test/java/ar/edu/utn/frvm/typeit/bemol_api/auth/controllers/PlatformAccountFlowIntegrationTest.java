package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.CreatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.CreatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PasskeyAuthenticator;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "spring.cache.type=redis",
      "management.health.mail.enabled=false",
      "app.mail.from=no-reply@example.test"
    })
@AutoConfigureMockMvc
@IntegrationTest
class PlatformAccountFlowIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String HOST = "admin.localhost:3000";
  private static final String ORIGIN = "http://" + HOST;
  private static final String BASE = "/api/v1/admin/auth";
  private static final String PASSWORD = "Test-password123";
  private static final String NEW_PASSWORD = "New-password456";

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate database;
  @Autowired private StringRedisTemplate redis;
  @Autowired private CreatePlatformAccountUseCase createAccount;
  @Autowired private PlatformAccountRepository accounts;
  @Autowired private PasswordEncoder passwords;
  @MockitoBean private JavaMailSender smtp;

  private final List<UUID> accountIds = new ArrayList<>();
  private final BlockingQueue<MimeMessage> mail = new LinkedBlockingQueue<>();

  private record Actor(PlatformAccount account, PasskeyAuthenticator key, JsonNode session) {}

  @BeforeEach
  void captureLocalMail() {
    when(smtp.createMimeMessage())
        .thenAnswer(ignored -> new MimeMessage(Session.getInstance(new Properties())));
    doAnswer(
            invocation -> {
              final MimeMessage message = invocation.getArgument(0);
              message.saveChanges();
              mail.add(message);
              return null;
            })
        .when(smtp)
        .send(any(MimeMessage.class));
  }

  @AfterEach
  void removeOwnFixtures() {
    for (final UUID id : accountIds) {
      for (final String table :
          List.of(
              "platform_email_change_tokens",
              "platform_refresh_tokens",
              "platform_sessions",
              "platform_account_roles",
              "platform_passkey_credentials")) {
        database.update("DELETE FROM " + table + " WHERE platform_account_id=?", id);
      }
      accounts.deleteById(id);
    }
  }

  @Test
  void profileUpdatesOnlyOwnNamesAndKeepsOtherDevicesAuthenticated() throws Exception {
    final var owner = actor();
    final var other = actor();
    final var second = login(owner, owner.account().getEmail(), PASSWORD);
    assertMe(owner.session(), 200);
    assertMe(second, 200);

    perform(
        put(BASE + "/account/profile"),
        Map.of(
            "name",
            " María ",
            "lastName",
            " González ",
            "platformAccountId",
            other.account().getId(),
            "email",
            "ignored@example.test",
            "password",
            NEW_PASSWORD,
            "roles",
            List.of()),
        owner.session(),
        204);

    final var updated = stored(owner);
    assertThat(updated.getName()).isEqualTo("María");
    assertThat(updated.getLastName()).isEqualTo("González");
    assertThat(updated.getEmail()).isEqualTo(owner.account().getEmail());
    assertThat(passwords.matches(PASSWORD, updated.getPassword())).isTrue();
    assertThat(stored(other).getName()).isEqualTo("Prueba");
    assertMe(owner.session(), 200);
    assertMe(second, 200);
  }

  @Test
  void sensitiveChangesRequireActionAndSessionBoundPasskeyProof() throws Exception {
    final var owner = actor();
    final var second = login(owner, owner.account().getEmail(), PASSWORD);
    final var body = Map.of("email", UUID.randomUUID() + "@example.test");
    perform(post(BASE + "/account/email-changes"), body, owner.session(), 403);
    reauthenticate(owner, owner.session(), "password");
    perform(post(BASE + "/account/email-changes"), body, owner.session(), 403);
    reauthenticate(owner, owner.session(), "email");
    perform(post(BASE + "/account/email-changes"), body, second, 403);
    perform(post(BASE + "/account/email-changes"), body, owner.session(), 204);
    perform(post(BASE + "/account/email-changes"), body, owner.session(), 403);
    assertThat(stored(owner).getEmail()).isEqualTo(owner.account().getEmail());
    assertMe(second, 200);
  }

  @Test
  void confirmedEmailRevokesCachedSessionsAndRefreshTokensWithoutRemovingPasskeys()
      throws Exception {
    final var owner = actor();
    final var second = login(owner, owner.account().getEmail(), PASSWORD);
    assertMe(owner.session(), 200);
    assertMe(second, 200);
    final String nextEmail = UUID.randomUUID() + "@example.test";
    final String token = requestEmail(owner, nextEmail);
    final String hash =
        database.queryForObject(
            "SELECT token_hash FROM platform_email_change_tokens WHERE platform_account_id=?",
            String.class,
            owner.account().getId());
    assertThat(hash).isEqualTo(JwtService.hashToken(token));
    assertThat(stored(owner).getEmail()).isEqualTo(owner.account().getEmail());
    assertMe(owner.session(), 200);

    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", token),
        owner.session(),
        204);

    assertThat(stored(owner).getEmail()).isEqualTo(nextEmail);
    assertRevoked(owner.session(), second);
    assertThat(activeKeys(owner)).isEqualTo(1);
    final var current = login(owner, nextEmail, PASSWORD);
    assertMe(current, 200);
    perform(post(BASE + "/account/email-changes/confirm"), Map.of("token", token), current, 400);
    assertThat(notification(owner.account().getEmail()).getContent().toString())
        .contains("cuenta de administración de Bemol");
    final String metadata =
        database
            .queryForList(
                "SELECT details FROM audit_events WHERE actor_platform_account_id=?",
                owner.account().getId())
            .toString();
    assertThat(metadata).doesNotContain(token, PASSWORD, "htmlBody", "token=");
  }

  @Test
  void emailConfirmationRejectsAnotherAccountAndExpiredLinksWithoutMutation() throws Exception {
    final var owner = actor();
    final var other = actor();
    final String token = requestEmail(owner, UUID.randomUUID() + "@example.test");
    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", token),
        other.session(),
        400);
    database.update(
        "UPDATE platform_email_change_tokens SET issued_at=now()-interval '2 days',expires_at=now()-interval '1 hour' WHERE platform_account_id=?",
        owner.account().getId());
    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", token),
        owner.session(),
        400);
    assertThat(stored(owner).getEmail()).isEqualTo(owner.account().getEmail());
    assertMe(owner.session(), 200);
    assertMe(other.session(), 200);
  }

  @Test
  void requestingAnotherLinkInvalidatesThePreviousToken() throws Exception {
    final var owner = actor();
    final String first = requestEmail(owner, UUID.randomUUID() + "@example.test");
    redis.delete("bemol:auth:action:mail:" + owner.account().getId());
    final String nextEmail = UUID.randomUUID() + "@example.test";
    final String second = requestEmail(owner, nextEmail);
    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", first),
        owner.session(),
        400);
    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", second),
        owner.session(),
        204);
    assertThat(stored(owner).getEmail()).isEqualTo(nextEmail);
  }

  @Test
  void passwordChangeChecksCurrentPasswordAndRevokesEverySessionAndPendingEmail() throws Exception {
    final var owner = actor();
    final var second = login(owner, owner.account().getEmail(), PASSWORD);
    requestEmail(owner, UUID.randomUUID() + "@example.test");
    assertMe(owner.session(), 200);
    assertMe(second, 200);
    // Start a fresh rate-limit window after creating the devices and pending email fixture.
    redis.delete("bemol:platform:attempt-limit:" + owner.account().getId());
    perform(post(BASE + "/account/password"), passwordBody(PASSWORD), owner.session(), 403);
    reauthenticate(owner, owner.session(), "password");
    perform(post(BASE + "/account/password"), passwordBody("incorrect"), owner.session(), 400);
    assertMe(owner.session(), 200);
    assertMe(second, 200);
    perform(post(BASE + "/account/password"), passwordBody(PASSWORD), owner.session(), 204);

    final var updated = stored(owner);
    assertThat(passwords.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
    assertThat(passwords.matches(PASSWORD, updated.getPassword())).isFalse();
    assertThat(pendingTokens(owner)).isZero();
    assertRevoked(owner.session(), second);
    assertThat(activeKeys(owner)).isEqualTo(1);
    perform(
        post(BASE + "/login"),
        Map.of("email", owner.account().getEmail(), "password", PASSWORD),
        null,
        401);
    assertMe(login(owner, owner.account().getEmail(), NEW_PASSWORD), 200);
  }

  @Test
  void passwordValidationRejectsMismatchAndBcryptOverflowWithoutClosingSessions() throws Exception {
    final var owner = actor();
    perform(
        post(BASE + "/account/password"),
        Map.of("currentPassword", PASSWORD, "password", NEW_PASSWORD, "confirmPassword", "other"),
        owner.session(),
        400);
    final String tooLong = "á".repeat(37);
    perform(
        post(BASE + "/account/password"),
        Map.of("currentPassword", PASSWORD, "password", tooLong, "confirmPassword", tooLong),
        owner.session(),
        400);
    assertThat(passwords.matches(PASSWORD, stored(owner).getPassword())).isTrue();
    assertMe(owner.session(), 200);
  }

  @Test
  void emailConflictAtConfirmationPreservesTheLosingAccountAndItsSessions() throws Exception {
    final var first = actor();
    final var second = actor();
    final String email = UUID.randomUUID() + "@example.test";
    final String firstToken = requestEmail(first, email);
    final String secondToken = requestEmail(second, email);
    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", firstToken),
        first.session(),
        204);
    perform(
        post(BASE + "/account/email-changes/confirm"),
        Map.of("token", secondToken),
        second.session(),
        409);
    assertThat(stored(first).getEmail()).isEqualTo(email);
    assertThat(stored(second).getEmail()).isEqualTo(second.account().getEmail());
    assertMe(second.session(), 200);
    assertThat(
            database.queryForObject(
                "SELECT used_at IS NULL FROM platform_email_change_tokens WHERE platform_account_id=?",
                Boolean.class,
                second.account().getId()))
        .isTrue();
  }

  @Test
  void administrativeEditorCannotBypassOwnSecurityAndInvalidatesOthersPendingChanges()
      throws Exception {
    final var owner = actor();
    final var support = actor();
    final String pending = requestEmail(owner, UUID.randomUUID() + "@example.test");
    final var body =
        Map.of(
            "name",
            "Prueba",
            "lastName",
            "Cuenta",
            "email",
            owner.account().getEmail(),
            "password",
            NEW_PASSWORD);
    final String endpoint = "/api/v1/admin/accounts/" + owner.account().getId();
    perform(put(endpoint), body, owner.session(), 400);
    assertThat(pendingTokens(owner)).isEqualTo(1);
    perform(put(endpoint), body, support.session(), 200);
    assertThat(pendingTokens(owner)).isZero();
    assertRevoked(owner.session());
    final var current = login(owner, owner.account().getEmail(), NEW_PASSWORD);
    perform(post(BASE + "/account/email-changes/confirm"), Map.of("token", pending), current, 400);
    assertMe(support.session(), 200);
  }

  @Test
  void concurrentConfirmationConsumesTheLinkOnlyOnce() throws Exception {
    final var owner = actor();
    final String email = UUID.randomUUID() + "@example.test";
    final String token = requestEmail(owner, email);
    final var start = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var tasks =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  ignored ->
                      executor.submit(
                          () -> {
                            start.await();
                            return mvc.perform(
                                    post(BASE + "/account/email-changes/confirm")
                                        .header("X-Institutional-Host", HOST)
                                        .header(
                                            "Authorization", "Bearer " + access(owner.session()))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(Map.of("token", token))))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                          }))
              .toList();
      start.countDown();
      final var statuses =
          List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS));
      assertThat(statuses.stream().filter(code -> code == 204).count()).isEqualTo(1);
      assertThat(statuses).allMatch(code -> code == 204 || code == 400 || code == 401);
    }
    assertThat(stored(owner).getEmail()).isEqualTo(email);
    assertRevoked(owner.session());
  }

  @Test
  void anonymousAndInstitutionalHostRequestsCannotModifyPlatformCredentials() throws Exception {
    perform(post(BASE + "/account/email-changes"), Map.of("email", "new@example.test"), null, 401);
    final var owner = actor();
    mvc.perform(
            post(BASE + "/account/password")
                .header("X-Institutional-Host", "localhost:3000")
                .header("Authorization", "Bearer " + access(owner.session()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(passwordBody(PASSWORD))))
        .andExpect(status().isForbidden());
    assertMe(owner.session(), 200);
  }

  private Actor actor() throws Exception {
    final String email = UUID.randomUUID() + "@example.test";
    final var created =
        createAccount.execute(
            new CreatePlatformAccountRequest("Prueba", "Plataforma", email, PASSWORD, true));
    final var account = accounts.findById(created.platformAccountId()).orElseThrow();
    accountIds.add(account.getId());
    final var key = new PasskeyAuthenticator();
    final var attempt =
        perform(post(BASE + "/login"), Map.of("email", email, "password", PASSWORD), null, 200);
    final String code = "a".repeat(64);
    redis
        .opsForValue()
        .set(
            "bemol:platform:setup:" + account.getId(),
            JwtService.hashToken(code),
            Duration.ofMinutes(5));
    final var options =
        perform(
            post(BASE + "/passkeys/setup/options"),
            Map.of(
                "loginAttemptId",
                attempt.path("loginAttemptId").asString(),
                "code",
                code,
                "label",
                "Equipo de prueba"),
            null,
            200);
    final var session =
        perform(
            post(BASE + "/passkeys/setup/verify"),
            Map.of(
                "ceremonyId",
                options.path("ceremonyId").asString(),
                "credential",
                key.register(options.path("options"), ORIGIN)),
            null,
            200);
    return new Actor(account, key, session);
  }

  private JsonNode login(final Actor actor, final String email, final String password)
      throws Exception {
    final var attempt =
        perform(post(BASE + "/login"), Map.of("email", email, "password", password), null, 200);
    final var options =
        perform(
            post(BASE + "/passkeys/authentication/options"),
            Map.of("loginAttemptId", attempt.path("loginAttemptId").asString()),
            null,
            200);
    return perform(
        post(BASE + "/passkeys/authentication/verify"),
        Map.of(
            "ceremonyId",
            options.path("ceremonyId").asString(),
            "credential",
            actor
                .key()
                .authenticate(
                    options.path("options"),
                    ORIGIN,
                    actor.account().getWebauthnUserHandle(),
                    true)),
        null,
        200);
  }

  private void reauthenticate(final Actor actor, final JsonNode session, final String target)
      throws Exception {
    final var options =
        perform(post(BASE + "/passkeys/reauth/options"), Map.of("target", target), session, 200);
    perform(
        post(BASE + "/passkeys/reauth/verify"),
        Map.of(
            "ceremonyId",
            options.path("ceremonyId").asString(),
            "credential",
            actor
                .key()
                .authenticate(
                    options.path("options"),
                    ORIGIN,
                    actor.account().getWebauthnUserHandle(),
                    true)),
        session,
        204);
  }

  private String requestEmail(final Actor actor, final String email) throws Exception {
    reauthenticate(actor, actor.session(), "email");
    perform(post(BASE + "/account/email-changes"), Map.of("email", email), actor.session(), 204);
    final String html = notification(email).getContent().toString();
    assertThat(html)
        .contains(ORIGIN + "/account/profile/email/confirm?token=")
        .doesNotContain("th:text", "th:src");
    final var matcher = Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(html);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private MimeMessage notification(final String email) throws Exception {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      final var message = mail.poll(100, TimeUnit.MILLISECONDS);
      if (message != null
          && requireNonNull(message.getAllRecipients())[0].toString().equals(email)) {
        return message;
      }
    }
    throw new AssertionError("Expected a local account notification");
  }

  private JsonNode perform(
      final MockHttpServletRequestBuilder request,
      final Object body,
      final @Nullable JsonNode session,
      final int expected)
      throws Exception {
    request
        .header("X-Institutional-Host", HOST)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
    if (session != null) {
      request.header("Authorization", "Bearer " + access(session));
    }
    final String content =
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return content.isBlank() ? json.createObjectNode() : json.readTree(content);
  }

  private void assertMe(final JsonNode session, final int expected) throws Exception {
    mvc.perform(
            get(BASE + "/me")
                .header("X-Institutional-Host", HOST)
                .header("Authorization", "Bearer " + access(session)))
        .andExpect(status().is(expected));
  }

  private void assertRevoked(final JsonNode... sessions) throws Exception {
    for (final var session : sessions) {
      assertMe(session, 401);
      perform(
          post(BASE + "/refresh"),
          Map.of("refreshToken", session.at("/tokens/refreshToken").asString()),
          null,
          401);
    }
  }

  private String access(final JsonNode session) {
    return session.at("/tokens/accessToken").asString();
  }

  private PlatformAccount stored(final Actor actor) {
    return accounts.findById(actor.account().getId()).orElseThrow();
  }

  private long pendingTokens(final Actor actor) {
    return requireNonNull(
        database.queryForObject(
            "SELECT count(*) FROM platform_email_change_tokens WHERE platform_account_id=?",
            Long.class,
            actor.account().getId()));
  }

  private long activeKeys(final Actor actor) {
    return requireNonNull(
        database.queryForObject(
            "SELECT count(*) FROM platform_passkey_credentials WHERE platform_account_id=? AND revoked_at IS NULL",
            Long.class,
            actor.account().getId()));
  }

  private Map<String, String> passwordBody(final String current) {
    return Map.of(
        "currentPassword", current, "password", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD);
  }
}
