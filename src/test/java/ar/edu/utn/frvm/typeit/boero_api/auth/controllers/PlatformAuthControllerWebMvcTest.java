package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.platformPrincipal;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PlatformAccountPayload;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.PlatformAccountResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.GetCurrentPlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PlatformLoginUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PlatformLogoutUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PlatformRefreshTokenUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
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

@WebMvcTest(PlatformAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformAuthControllerWebMvcTest {

  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private PlatformLoginUseCase platformLoginUseCase;
  @MockitoBean private PlatformRefreshTokenUseCase platformRefreshTokenUseCase;
  @MockitoBean private PlatformLogoutUseCase platformLogoutUseCase;
  @MockitoBean private GetCurrentPlatformAccountUseCase getCurrentPlatformAccountUseCase;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Test
  @DisplayName("Should return current platform account")
  void shouldReturnCurrentPlatformAccount() throws Exception {
    JwtAuthenticatedPlatformAccount principal = platformPrincipal(PLATFORM_ACCOUNT_ID);
    when(getCurrentPlatformAccountUseCase.execute(principal))
        .thenReturn(
            PlatformAccountResponse.builder()
                .account(
                    PlatformAccountPayload.builder()
                        .platformAccountId(PLATFORM_ACCOUNT_ID)
                        .email("admin@plataforma.com")
                        .name("Juan")
                        .lastName("Perez")
                        .build())
                .build());

    mockMvc
        .perform(
            get("/api/v1/admin/auth/me").principal(new TestingAuthenticationToken(principal, "")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.platformAccountId").value(PLATFORM_ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.account.email").value("admin@plataforma.com"))
        .andExpect(jsonPath("$.account.name").value("Juan"))
        .andExpect(jsonPath("$.account.lastName").value("Perez"));
  }

  @Test
  @DisplayName("Should reject platform login when email and password are missing")
  void shouldRejectPlatformLoginWhenEmailAndPasswordAreMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Se encontraron errores de validación."))
        .andExpect(jsonPath("$.fieldErrors.email").value("El correo electrónico es requerido."))
        .andExpect(jsonPath("$.fieldErrors.password").value("La contraseña es requerida."));

    verifyNoInteractions(platformLoginUseCase);
  }

  @Test
  @DisplayName("Should reject platform login when email format is invalid")
  void shouldRejectPlatformLoginWhenEmailFormatIsInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "not-an-email",
                      "password": "password123"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Se encontraron errores de validación."))
        .andExpect(
            jsonPath("$.fieldErrors.email")
                .value("El correo electrónico debe tener un formato válido."));

    verifyNoInteractions(platformLoginUseCase);
  }
}
