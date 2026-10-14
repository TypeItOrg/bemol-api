package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticationFilter;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.DiscoverablePasskeyLoginUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.InstitutionalAccessTokenInput;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.InstitutionalHostContext;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ListPasskeysUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PlatformAccessTokenInput;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ReAuthenticateUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RenamePasskeyUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RequestPasskeyAuthenticationOptionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RequestPasskeyRegistrationOptionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RevokePasskeyUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.VerifyPasskeyAuthenticationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.VerifyPasskeyRegistrationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.config.TimeConfig;
import ar.edu.utn.frvm.typeit.boero_api.security.config.SecurityConfig;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.CustomAccessDeniedHandler;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.CustomAuthenticationEntryPoint;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorResponseWriter;
import ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

@WebMvcTest(PasskeyController.class)
@Import({
  TimeConfig.class,
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  CustomAuthenticationEntryPoint.class,
  CustomAccessDeniedHandler.class,
  SecurityErrorResponseWriter.class,
  JwtService.class,
  InstitutionalCallerGuard.class,
  GlobalExceptionHandler.class
})
class PasskeyControllerSecurityWebMvcTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PERSON_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @TestConfiguration
  static class SecurityTestConfig {
    @Bean
    JwtProperties jwtProperties() {
      return AuthTestData.jwtProperties();
    }

    @Bean
    PathMatcher pathMatcher() {
      return new AntPathMatcher();
    }

    @Bean
    com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
      return new com.fasterxml.jackson.databind.ObjectMapper();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  @MockitoBean private ListPasskeysUseCase listPasskeysUseCase;
  @MockitoBean private RequestPasskeyRegistrationOptionsUseCase requestRegistrationOptionsUseCase;
  @MockitoBean private VerifyPasskeyRegistrationUseCase verifyPasskeyRegistrationUseCase;
  @MockitoBean private RenamePasskeyUseCase renamePasskeyUseCase;
  @MockitoBean private RevokePasskeyUseCase revokePasskeyUseCase;

  @MockitoBean
  private RequestPasskeyAuthenticationOptionsUseCase requestAuthenticationOptionsUseCase;

  @MockitoBean private VerifyPasskeyAuthenticationUseCase verifyPasskeyAuthenticationUseCase;
  @MockitoBean private DiscoverablePasskeyLoginUseCase discoverablePasskeyLoginUseCase;
  @MockitoBean private InstitutionalHostContext institutionalHostContext;
  @MockitoBean private ReAuthenticateUseCase reAuthenticateUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private WebAuthnProperties webAuthnProperties;

  @BeforeEach
  void stubTokenInfrastructure() {
    when(tokenBlacklistService.isBlacklisted(any())).thenReturn(false);
    when(isSessionActiveUseCase.execute(any())).thenReturn(true);
    when(isPlatformSessionActiveUseCase.execute(any())).thenReturn(true);
  }

  @Test
  @DisplayName("Should reject anonymous access to passkey endpoints")
  void shouldRejectAnonymousAccessToPasskeyEndpoints() throws Exception {
    mockMvc.perform(get("/api/v1/auth/passkeys")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Should forbid platform principal on institutional passkey endpoints")
  void shouldForbidPlatformPrincipalOnInstitutionalPasskeyEndpoints() throws Exception {
    String platformToken =
        jwtService.generatePlatformAccessToken(
            new PlatformAccessTokenInput(PLATFORM_ACCOUNT_ID, "admin@plataforma.com", SESSION_ID));

    mockMvc
        .perform(
            get("/api/v1/auth/passkeys").header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/auth/re-authenticate")
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"password": "password123"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should allow institutional principal on passkey endpoints")
  void shouldAllowInstitutionalPrincipalOnPasskeyEndpoints() throws Exception {
    when(webAuthnProperties.maxPasskeys()).thenReturn(10);
    when(listPasskeysUseCase.execute(any()))
        .thenReturn(
            List.of(new PasskeyResponse(UUID.randomUUID(), "Llave", Instant.now(), Instant.now())));

    mockMvc
        .perform(
            get("/api/v1/auth/passkeys")
                .header(HttpHeaders.AUTHORIZATION, bearer(institutionalToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxActivePasskeys").value(10));
  }

  private String institutionalToken() {
    return jwtService.generateAccessToken(
        InstitutionalAccessTokenInput.builder()
            .userId(USER_ID)
            .personId(PERSON_ID)
            .institutionId(INSTITUTION_ID)
            .documentNumber("12345678")
            .sessionId(SESSION_ID)
            .build());
  }

  private static String bearer(final String token) {
    return "Bearer " + token;
  }
}
