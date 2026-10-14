package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.RecentAuthRequiredException;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.DiscoverablePasskeyLoginUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ListPasskeysUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ReAuthenticateUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RenamePasskeyUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RequestPasskeyAuthenticationOptionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RequestPasskeyRegistrationOptionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RevokePasskeyUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.VerifyPasskeyAuthenticationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.VerifyPasskeyRegistrationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(PasskeyController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasskeyControllerWebMvcTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PASSKEY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private ListPasskeysUseCase listPasskeysUseCase;
  @MockitoBean private RequestPasskeyRegistrationOptionsUseCase requestRegistrationOptionsUseCase;
  @MockitoBean private VerifyPasskeyRegistrationUseCase verifyPasskeyRegistrationUseCase;
  @MockitoBean private RenamePasskeyUseCase renamePasskeyUseCase;
  @MockitoBean private RevokePasskeyUseCase revokePasskeyUseCase;

  @MockitoBean
  private RequestPasskeyAuthenticationOptionsUseCase requestAuthenticationOptionsUseCase;

  @MockitoBean private VerifyPasskeyAuthenticationUseCase verifyPasskeyAuthenticationUseCase;
  @MockitoBean private DiscoverablePasskeyLoginUseCase discoverablePasskeyLoginUseCase;
  @MockitoBean private ReAuthenticateUseCase reAuthenticateUseCase;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private WebAuthnProperties webAuthnProperties;
  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;

  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Test
  @DisplayName("Should expose authentication options without user data")
  void shouldExposeAuthenticationOptions() throws Exception {
    final ObjectMapper mapper = JsonMapper.builder().build();
    when(requestAuthenticationOptionsUseCase.execute("attempt"))
        .thenReturn(
            new PasskeyAuthenticationOptionsResponse(
                "ceremony",
                mapper.readTree(
                    """
                    {
                      "challenge": "AQID",
                      "rpId": "localhost",
                      "timeout": 300000,
                      "allowCredentials": [],
                      "userVerification": "required"
                    }
                    """)));

    mockMvc
        .perform(
            post("/api/v1/auth/passkeys/authentication/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginAttemptId\":\"attempt\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ceremonyId").value("ceremony"))
        .andExpect(jsonPath("$.options.rpId").value("localhost"))
        .andExpect(jsonPath("$.options.challenge").value("AQID"))
        .andExpect(jsonPath("$.options.userVerification").value("required"))
        .andExpect(jsonPath("$.options.allowCredentials").isEmpty());
  }

  @Test
  @DisplayName("Should reject authentication options without attempt")
  void shouldRejectAuthenticationOptionsWithoutAttempt() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/passkeys/authentication/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginAttemptId\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should list active passkeys without revoked metadata")
  void shouldListActivePasskeys() throws Exception {
    final JwtAuthenticatedUser principal = principal();
    when(listPasskeysUseCase.execute(principal))
        .thenReturn(List.of(new PasskeyResponse(PASSKEY_ID, "Mi PC", Instant.now(), null)));
    when(webAuthnProperties.maxPasskeys()).thenReturn(10);

    mockMvc
        .perform(get("/api/v1/auth/passkeys").principal(authentication(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passkeys[0].id").value(PASSKEY_ID.toString()))
        .andExpect(jsonPath("$.passkeys[0].label").value("Mi PC"))
        .andExpect(jsonPath("$.maxActivePasskeys").value(10));
  }

  @Test
  @DisplayName("Should reject registration options with blank label")
  void shouldRejectRegistrationOptionsWithBlankLabel() throws Exception {
    final JwtAuthenticatedUser principal = principal();

    mockMvc
        .perform(
            post("/api/v1/auth/passkeys/registration/options")
                .principal(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should request registration options with a valid label")
  void shouldRequestRegistrationOptions() throws Exception {
    final JwtAuthenticatedUser principal = principal();
    final ObjectMapper mapper = JsonMapper.builder().build();
    when(requestRegistrationOptionsUseCase.execute(any(), any()))
        .thenReturn(
            new PasskeyRegistrationOptionsResponse(
                "ceremony",
                mapper.readTree(
                    """
                    {
                      "challenge": "AQID",
                      "rp": {"id": "localhost", "name": "Boero"},
                      "user": {"id": "AQID", "name": "user", "displayName": "User"},
                      "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
                      "timeout": 300000,
                      "excludeCredentials": [],
                      "authenticatorSelection": {"residentKey": "required", "userVerification": "required"},
                      "attestation": "none"
                    }
                    """)));

    mockMvc
        .perform(
            post("/api/v1/auth/passkeys/registration/options")
                .principal(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Mi PC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ceremonyId").value("ceremony"))
        .andExpect(jsonPath("$.options.rp.id").value("localhost"))
        .andExpect(jsonPath("$.options.user.name").value("user"))
        .andExpect(jsonPath("$.options.pubKeyCredParams[0].alg").value(-7))
        .andExpect(jsonPath("$.options.excludeCredentials").isEmpty());
  }

  @Test
  @DisplayName("Should return a machine-readable code when recent authentication is required")
  void shouldReturnCodeWhenRecentAuthRequired() throws Exception {
    final JwtAuthenticatedUser principal = principal();
    doThrow(new RecentAuthRequiredException()).when(revokePasskeyUseCase).execute(any(), any());

    mockMvc
        .perform(delete("/api/v1/auth/passkeys/" + PASSKEY_ID).principal(authentication(principal)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RECENT_AUTHENTICATION_REQUIRED"));
  }

  @Test
  @DisplayName("Should reject rename with oversized label")
  void shouldRejectRenameWithOversizedLabel() throws Exception {
    final JwtAuthenticatedUser principal = principal();

    mockMvc
        .perform(
            patch("/api/v1/auth/passkeys/" + PASSKEY_ID)
                .principal(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"" + "x".repeat(101) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  private static JwtAuthenticatedUser principal() {
    return institutionalPrincipal(USER_ID, INSTITUTION_ID, SESSION_ID);
  }

  private static TestingAuthenticationToken authentication(JwtAuthenticatedUser principal) {
    return new TestingAuthenticationToken(principal, "");
  }
}
