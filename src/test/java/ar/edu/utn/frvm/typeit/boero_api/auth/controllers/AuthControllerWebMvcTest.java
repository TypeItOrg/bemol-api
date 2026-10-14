package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.ActiveSessionResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.TokenResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserPayload;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserRegisteredResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.GetActiveSessionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.GetCurrentUserUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IdentifyLoginUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.LogoutUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PasswordLoginWithAttemptUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RefreshTokenUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RegisterUserUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RequestInstitutionalPasswordRecoveryUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ResetInstitutionalPasswordUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PERSON_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private RegisterUserUseCase registerUserUseCase;
  @MockitoBean private RefreshTokenUseCase refreshTokenUseCase;

  @MockitoBean private IdentifyLoginUseCase identifyLoginUseCase;

  @MockitoBean private PasswordLoginWithAttemptUseCase passwordLoginWithAttemptUseCase;

  @MockitoBean
  private RequestInstitutionalPasswordRecoveryUseCase requestInstitutionalPasswordRecoveryUseCase;

  @MockitoBean private ResetInstitutionalPasswordUseCase resetInstitutionalPasswordUseCase;
  @MockitoBean private LogoutUseCase logoutUseCase;
  @MockitoBean private GetActiveSessionsUseCase getActiveSessionsUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;

  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Test
  @DisplayName("Should register user and return created response")
  void shouldRegisterUserAndReturnCreatedResponse() throws Exception {
    when(registerUserUseCase.execute(any(RegisterRequest.class)))
        .thenReturn(
            UserRegisteredResponse.builder()
                .userId(USER_ID)
                .documentNumber("12345678")
                .institutionId(INSTITUTION_ID)
                .build());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Ana",
                      "lastName": "Garcia",
                      "birthDate": "2010-01-01",
                      "documentNumber": "12345678",
                      "email": "ana@example.com",
                      "password": "password123",
                      "institutionId": "22222222-2222-2222-2222-222222222222"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.documentNumber").value("12345678"))
        .andExpect(jsonPath("$.institutionId").value(INSTITUTION_ID.toString()));
  }

  @Test
  @DisplayName("Should not expose the removed legacy direct login endpoint")
  void shouldNotExposeRemovedLegacyDirectLoginEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "documentNumber": "12345678",
                      "password": "password123",
                      "institutionId": "22222222-2222-2222-2222-222222222222",
                      "rememberMe": true
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("Should refresh tokens and return auth response")
  void shouldRefreshTokensAndReturnAuthResponse() throws Exception {
    when(refreshTokenUseCase.execute(any(RefreshTokenRequest.class))).thenReturn(authResponse());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.documentNumber").value("12345678"))
        .andExpect(jsonPath("$.tokens.refreshToken").value("refresh-token"));
  }

  @Test
  @DisplayName("Should return current user")
  void shouldReturnCurrentUser() throws Exception {
    JwtAuthenticatedUser principal = principal();
    when(getCurrentUserUseCase.execute(principal))
        .thenReturn(
            UserResponse.builder()
                .user(
                    UserPayload.builder()
                        .userId(USER_ID)
                        .personId(PERSON_ID)
                        .name("Ana")
                        .lastName("Garcia")
                        .documentNumber("12345678")
                        .institutionId(INSTITUTION_ID)
                        .roles(List.of("Preceptor"))
                        .build())
                .build());

    mockMvc
        .perform(get("/api/v1/auth/me").principal(authentication(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.user.personId").value(PERSON_ID.toString()))
        .andExpect(jsonPath("$.user.roles[0]").value("Preceptor"));
  }

  @Test
  @DisplayName("Should return active sessions")
  void shouldReturnActiveSessions() throws Exception {
    JwtAuthenticatedUser principal = principal();
    ActiveSessionResponse session =
        ActiveSessionResponse.builder()
            .sessionId(SESSION_ID)
            .ipAddress("192.0.2.10")
            .userAgent("Mozilla/5.0")
            .startedAt(Instant.now())
            .currentSession(true)
            .build();
    PaginatedResponse<ActiveSessionResponse> response =
        PaginatedResponse.<ActiveSessionResponse>builder()
            .items(List.of(session))
            .page(0)
            .size(20)
            .totalItems(1)
            .totalPages(1)
            .build();
    when(getActiveSessionsUseCase.execute(eq(principal), any(Pageable.class))).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/auth/sessions").principal(authentication(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  @DisplayName("Should logout and pass bearer token without prefix")
  void shouldLogoutAndPassBearerTokenWithoutPrefix() throws Exception {
    JwtAuthenticatedUser principal = principal();

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .principal(authentication(principal))
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andExpect(status().isNoContent());

    verify(logoutUseCase).execute(principal, "access-token");
  }

  @ParameterizedTest(name = "{0}: {2}")
  @MethodSource("invalidRegisterFields")
  void shouldRejectInvalidRegistrationBeforeCallingUseCase(
      final String field, final Object value, final String expectedMessage) throws Exception {
    final Map<String, Object> request =
        new HashMap<>(
            Map.of(
                "name", "Ana",
                "lastName", "Garcia",
                "birthDate", "2010-01-01",
                "documentNumber", "12345678",
                "email", "ana@example.com",
                "password", "password123",
                "institutionId", INSTITUTION_ID.toString()));
    request.put(field, value);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Se encontraron errores de validación."))
        .andExpect(jsonPath("$.fieldErrors", aMapWithSize(1)))
        .andExpect(jsonPath("$.fieldErrors." + field).value(expectedMessage));

    verifyNoInteractions(registerUserUseCase);
  }

  private static Stream<Arguments> invalidRegisterFields() {
    return Stream.of(
        Arguments.of("email", null, "El email es requerido."),
        Arguments.of(
            "documentNumber",
            "123",
            "El número de documento debe tener exactamente 8 dígitos numéricos."),
        Arguments.of("institutionId", null, "La institución es requerida."),
        Arguments.of("birthDate", null, "La fecha de nacimiento es requerida."),
        Arguments.of("password", "short", "La contraseña debe tener al menos 8 caracteres."));
  }

  @Test
  @DisplayName("Should reject refresh when refresh token is blank")
  void shouldRejectRefreshWhenRefreshTokenIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Se encontraron errores de validación."))
        .andExpect(
            jsonPath("$.fieldErrors.refreshToken")
                .value("El token de actualización es requerido."));

    verifyNoInteractions(refreshTokenUseCase);
  }

  private static AuthResponse authResponse() {
    return AuthResponse.builder()
        .user(
            UserPayload.builder()
                .userId(USER_ID)
                .personId(PERSON_ID)
                .name("Ana")
                .lastName("Garcia")
                .documentNumber("12345678")
                .institutionId(INSTITUTION_ID)
                .build())
        .tokens(
            TokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build())
        .build();
  }

  private static JwtAuthenticatedUser principal() {
    return institutionalPrincipal(USER_ID, INSTITUTION_ID, SESSION_ID);
  }

  private static TestingAuthenticationToken authentication(JwtAuthenticatedUser principal) {
    return new TestingAuthenticationToken(principal, "");
  }
}
