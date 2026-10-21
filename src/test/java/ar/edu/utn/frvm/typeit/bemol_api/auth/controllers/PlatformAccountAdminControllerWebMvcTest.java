package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountAdminResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.CreatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.GetPlatformAccountAdminUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ListPlatformAccountsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.UpdatePlatformAccountStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.UpdatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(PlatformAccountAdminController.class)
@Import({RoleAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PlatformAccountAdminControllerWebMvcTest {

  private static final UUID CURRENT_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TARGET_ACCOUNT_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListPlatformAccountsUseCase listPlatformAccountsUseCase;
  @MockitoBean private GetPlatformAccountAdminUseCase getPlatformAccountAdminUseCase;
  @MockitoBean private CreatePlatformAccountUseCase createPlatformAccountUseCase;
  @MockitoBean private UpdatePlatformAccountUseCase updatePlatformAccountUseCase;
  @MockitoBean private UpdatePlatformAccountStatusUseCase updatePlatformAccountStatusUseCase;

  @Test
  @DisplayName("Should forbid unauthenticated platform account listing")
  void list_returnsForbiddenWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/admin/accounts")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should list platform accounts for platform administrator")
  void list_returnsAccountsForPlatformAdministrator() throws Exception {
    final var authentication = platformAuthentication();
    allowPlatformAdmin();
    when(listPlatformAccountsUseCase.execute(any(), any(), any()))
        .thenReturn(
            PaginatedResponse.<PlatformAccountAdminResponse>builder()
                .items(List.of(accountResponse()))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(get("/api/v1/admin/accounts").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].platformAccountId").value(TARGET_ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.items[0].roleCode").value("PLATFORM_ADMIN"));
  }

  @Test
  @DisplayName("Should create platform account without exposing password")
  void create_returnsCreatedAccountWithoutPassword() throws Exception {
    final var authentication = platformAuthentication();
    allowPlatformAdmin();
    when(createPlatformAccountUseCase.execute(any())).thenReturn(accountResponse());

    mockMvc
        .perform(
            post("/api/v1/admin/accounts")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "María",
                      "lastName": "González",
                      "email": "maria@boero.edu.ar",
                      "password": "password123"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("maria@boero.edu.ar"))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  @DisplayName("Should update platform account without exposing password")
  void update_returnsUpdatedAccountWithoutPassword() throws Exception {
    final var authentication = platformAuthentication();
    allowPlatformAdmin();
    when(updatePlatformAccountUseCase.execute(eq(TARGET_ACCOUNT_ID), any()))
        .thenReturn(accountResponse());

    mockMvc
        .perform(
            put("/api/v1/admin/accounts/{id}", TARGET_ACCOUNT_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "María",
                      "lastName": "González",
                      "email": "maria@boero.edu.ar",
                      "password": ""
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("maria@boero.edu.ar"))
        .andExpect(jsonPath("$.password").doesNotExist());

    verify(updatePlatformAccountUseCase).execute(eq(TARGET_ACCOUNT_ID), any());
  }

  @Test
  @DisplayName("Should pass current account id when updating platform account status")
  void updateStatus_passesAuthenticatedAccountId() throws Exception {
    final var authentication = platformAuthentication();
    allowPlatformAdmin();

    mockMvc
        .perform(
            patch("/api/v1/admin/accounts/{id}/status", TARGET_ACCOUNT_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isNoContent());

    verify(updatePlatformAccountStatusUseCase)
        .execute(TARGET_ACCOUNT_ID, CURRENT_ACCOUNT_ID, false);
  }

  private TestingAuthenticationToken platformAuthentication() {
    return new TestingAuthenticationToken(platformPrincipal(CURRENT_ACCOUNT_ID), "");
  }

  private void allowPlatformAdmin() {
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(true);
  }

  private static PlatformAccountAdminResponse accountResponse() {
    return PlatformAccountAdminResponse.builder()
        .platformAccountId(TARGET_ACCOUNT_ID)
        .name("María")
        .lastName("González")
        .email("maria@boero.edu.ar")
        .enabled(true)
        .createdAt(
            java.time.LocalDateTime.of(2026, 7, 13, 20, 0).toInstant(java.time.ZoneOffset.UTC))
        .roleCode(PlatformRoleCode.PLATFORM_ADMIN)
        .roleName(PlatformRoleCode.PLATFORM_ADMIN.getDisplayName())
        .build();
  }
}
