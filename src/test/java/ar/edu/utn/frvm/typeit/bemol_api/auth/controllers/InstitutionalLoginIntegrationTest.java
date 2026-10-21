package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.auth.refresh-replay.ttl=1m")
@AutoConfigureMockMvc
@IntegrationTest
class InstitutionalLoginIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate database;
  @Autowired private JwtProperties jwtProperties;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private PasswordEncoder passwords;

  @ParameterizedTest(name = "Institutional login, rotation and logout, rememberMe={0}")
  @ValueSource(booleans = {false, true})
  void loginStaysInItsInstitutionAndLogoutRevokesTheWholeSession(final boolean rememberMe)
      throws Exception {
    final var account = account("password123");
    final var sibling = account("sibling-password");
    final var identification =
        mockMvc
            .perform(
                post("/api/v1/auth/login/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"institutionId":"%s","documentNumber":"43218765"}
                        """
                            .formatted(account.institutionId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextStep").value("PASSWORD"))
            .andReturn();
    final String attempt =
        jsonMapper
            .readTree(identification.getResponse().getContentAsString())
            .get("loginAttemptId")
            .asString();
    mockMvc
        .perform(
            post("/api/v1/auth/login/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"loginAttemptId":"%s","password":"sibling-password"}
                    """
                        .formatted(attempt)))
        .andExpect(status().isUnauthorized());

    final Instant beforeLogin = Instant.now();
    final var login =
        mockMvc
            .perform(
                post("/api/v1/auth/login/password")
                    .with(
                        request -> {
                          request.setRemoteAddr("192.0.2.10");
                          return request;
                        })
                    .header(HttpHeaders.USER_AGENT, "Institutional login flow")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"loginAttemptId":"%s","password":"password123","rememberMe":%s}
                        """
                            .formatted(attempt, rememberMe)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.userId").value(account.userId().toString()))
            .andExpect(jsonPath("$.user.personId").value(account.personId().toString()))
            .andExpect(jsonPath("$.user.institutionId").value(account.institutionId().toString()))
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andReturn();
    final var body = jsonMapper.readTree(login.getResponse().getContentAsString());
    final String accessToken = body.at("/tokens/accessToken").asString();
    final String refreshToken = body.at("/tokens/refreshToken").asString();
    final var session =
        database.queryForMap("SELECT * FROM user_sessions WHERE user_id = ?", account.userId());
    final UUID sessionId = (UUID) session.get("user_session_id");
    final var stored =
        database.queryForMap("SELECT * FROM refresh_tokens WHERE session_id = ?", sessionId);
    final String oldHash = JwtService.hashToken(refreshToken);

    assertThat(session)
        .containsEntry("ip_address", "192.0.2.10")
        .containsEntry("user_agent", "Institutional login flow")
        .containsEntry("remember_me", rememberMe)
        .containsEntry("active", true);
    assertThat(stored.get("token_hash")).isEqualTo(oldHash).isNotEqualTo(refreshToken);
    assertThat(requireNonNull((java.sql.Timestamp) stored.get("expires_at")).toInstant())
        .isBetween(
            beforeLogin.plus(jwtProperties.refreshExpiration(rememberMe)),
            Instant.now().plus(jwtProperties.refreshExpiration(rememberMe)));
    assertThat(
            database.queryForObject(
                "SELECT count(*) FROM user_sessions WHERE user_id = ?",
                Long.class,
                sibling.userId()))
        .isZero();
    assertAuthenticated(accessToken, account);

    final var rotated =
        jsonMapper.readTree(
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    final String nextAccess = rotated.at("/tokens/accessToken").asString();
    final String nextRefresh = rotated.at("/tokens/refreshToken").asString();
    assertThat(nextRefresh).isNotBlank().isNotEqualTo(refreshToken);
    assertThat(
            database.queryForObject(
                "SELECT revoked FROM refresh_tokens WHERE token_hash = ?", Boolean.class, oldHash))
        .isTrue();
    assertThat(
            database.queryForMap(
                "SELECT token_hash, family_id FROM refresh_tokens WHERE session_id = ? AND NOT revoked",
                sessionId))
        .containsEntry("token_hash", JwtService.hashToken(nextRefresh))
        .containsEntry("family_id", stored.get("family_id"));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokens.accessToken").value(nextAccess))
        .andExpect(jsonPath("$.tokens.refreshToken").value(nextRefresh));
    assertAuthenticated(nextAccess, account);

    mockMvc
        .perform(
            post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + nextAccess))
        .andExpect(status().isNoContent());
    assertThat(
            database.queryForObject(
                "SELECT active FROM user_sessions WHERE user_session_id = ?",
                Boolean.class,
                sessionId))
        .isFalse();
    assertThat(
            database.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND NOT revoked",
                Long.class,
                sessionId))
        .isZero();
    for (final String token : new String[] {accessToken, nextAccess}) {
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }
    for (final String token : new String[] {refreshToken, nextRefresh}) {
      mockMvc
          .perform(
              post("/api/v1/auth/refresh")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"" + token + "\"}"))
          .andExpect(status().isUnauthorized());
    }
  }

  private void assertAuthenticated(final String accessToken, final Account account)
      throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.userId").value(account.userId().toString()))
        .andExpect(jsonPath("$.user.institutionId").value(account.institutionId().toString()));
  }

  private Account account(final String password) {
    return requireNonNull(
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  final var institution =
                      InstitutionalTestData.createInstitution(
                          entityManager, "login-" + UUID.randomUUID());
                  final var person =
                      InstitutionalTestData.persist(
                          entityManager, InstitutionalTestData.person(institution, "43218765"));
                  final var user =
                      User.builder()
                          .person(person)
                          .institution(institution)
                          .password(requireNonNull(passwords.encode(password)))
                          .build();
                  entityManager.persist(user);
                  return new Account(user.getId(), person.getId(), institution.getId());
                }));
  }

  private record Account(UUID userId, UUID personId, UUID institutionId) {}
}
