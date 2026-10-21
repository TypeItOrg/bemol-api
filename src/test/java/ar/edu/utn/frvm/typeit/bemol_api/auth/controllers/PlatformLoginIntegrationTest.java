package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.AccessTokenParseResult;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.auth.refresh-replay.ttl=1m")
@AutoConfigureMockMvc
@IntegrationTest
class PlatformLoginIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate database;
  @Autowired private JwtService jwt;
  @Autowired private JwtProperties jwtProperties;

  @ParameterizedTest(name = "Platform login, rotation and logout, rememberMe={0}")
  @ValueSource(booleans = {false, true})
  void loginRefreshAndLogoutUseRealPersistenceAndRevocation(final boolean rememberMe)
      throws Exception {
    final Instant beforeLogin = Instant.now();
    final var login =
        mockMvc
            .perform(
                post("/api/v1/admin/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"admin@plataforma.com","password":"admin123","rememberMe":%s}
                        """
                            .formatted(rememberMe)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.email").value("admin@plataforma.com"))
            .andExpect(jsonPath("$.account.name").value("Administrador"))
            .andExpect(jsonPath("$.account.lastName").value("Plataforma"))
            .andExpect(jsonPath("$.account.password").doesNotExist())
            .andReturn();
    final var body = jsonMapper.readTree(login.getResponse().getContentAsString());
    final String accessToken = body.at("/tokens/accessToken").asString();
    final String refreshToken = body.at("/tokens/refreshToken").asString();
    final UUID accountId = UUID.fromString(body.at("/account/platformAccountId").asString());
    final var claims = ((AccessTokenParseResult.Ok) jwt.parseAccessToken(accessToken)).claims();
    final UUID sessionId = jwt.extractSessionId(claims);
    final String oldHash = JwtService.hashToken(refreshToken);
    final var stored =
        database.queryForMap(
            "SELECT token_hash, platform_account_id, family_id, expires_at FROM platform_refresh_tokens WHERE platform_session_id = ?",
            sessionId);

    assertThat(stored.get("token_hash")).isEqualTo(oldHash).isNotEqualTo(refreshToken);
    assertThat(stored.get("platform_account_id")).isEqualTo(accountId);
    assertThat(requireNonNull((java.sql.Timestamp) stored.get("expires_at")).toInstant())
        .isBetween(
            beforeLogin.plus(jwtProperties.refreshExpiration(rememberMe)),
            Instant.now().plus(jwtProperties.refreshExpiration(rememberMe)));
    assertThat(
            database.queryForObject(
                "SELECT remember_me FROM platform_sessions WHERE platform_session_id = ?",
                Boolean.class,
                sessionId))
        .isEqualTo(rememberMe);
    assertAuthenticated(accessToken, accountId);

    final var rotated =
        jsonMapper.readTree(
            mockMvc
                .perform(
                    post("/api/v1/admin/auth/refresh")
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
                "SELECT revoked FROM platform_refresh_tokens WHERE token_hash = ?",
                Boolean.class,
                oldHash))
        .isTrue();
    assertThat(
            database.queryForMap(
                "SELECT token_hash, family_id, platform_account_id FROM platform_refresh_tokens WHERE platform_session_id = ? AND NOT revoked",
                sessionId))
        .containsEntry("token_hash", JwtService.hashToken(nextRefresh))
        .containsEntry("family_id", stored.get("family_id"))
        .containsEntry("platform_account_id", accountId);
    mockMvc
        .perform(
            post("/api/v1/admin/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokens.accessToken").value(nextAccess))
        .andExpect(jsonPath("$.tokens.refreshToken").value(nextRefresh));
    assertAuthenticated(nextAccess, accountId);

    mockMvc
        .perform(
            post("/api/v1/admin/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + nextAccess))
        .andExpect(status().isNoContent());
    assertThat(
            database.queryForObject(
                "SELECT active FROM platform_sessions WHERE platform_session_id = ?",
                Boolean.class,
                sessionId))
        .isFalse();
    assertThat(
            database.queryForObject(
                "SELECT count(*) FROM platform_refresh_tokens WHERE platform_session_id = ? AND NOT revoked",
                Long.class,
                sessionId))
        .isZero();
    for (final String token : new String[] {accessToken, nextAccess}) {
      mockMvc
          .perform(
              get("/api/v1/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }
    for (final String token : new String[] {refreshToken, nextRefresh}) {
      mockMvc
          .perform(
              post("/api/v1/admin/auth/refresh")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"" + token + "\"}"))
          .andExpect(status().isUnauthorized());
    }
  }

  private void assertAuthenticated(final String accessToken, final UUID accountId)
      throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.platformAccountId").value(accountId.toString()));
  }
}
