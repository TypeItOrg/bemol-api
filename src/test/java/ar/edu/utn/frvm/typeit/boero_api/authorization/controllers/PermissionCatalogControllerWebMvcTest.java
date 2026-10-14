package ar.edu.utn.frvm.typeit.boero_api.authorization.controllers;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.config.WebConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(PermissionCatalogController.class)
@Import({RoleAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PermissionCatalogControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;

  @Test
  @DisplayName("Should forbid unauthenticated access to permission catalog")
  void list_returnsForbiddenWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/admin/permissions")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should forbid institutional principals from permission catalog")
  void list_returnsForbiddenForInstitutionalPrincipal() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    stubPlatformAdminAccess(false);

    mockMvc
        .perform(get("/api/v1/admin/permissions").principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should return institution permissions for platform admin")
  void list_returnsInstitutionPermissionsForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);

    mockMvc
        .perform(get("/api/v1/admin/permissions").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(82))
        .andExpect(jsonPath("$[0].scope").value("INSTITUTION"))
        .andExpect(jsonPath("$[?(@.code == 'institution:study-plan:delete')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.code == 'institution:study-plan:restore')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.code == 'institution:shift:read')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.code == 'institution:shift:restore')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.code == 'institution:academic-offer:read')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.code == 'institution:document-catalog:read')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.code == 'institution:document-catalog:manage')]").isNotEmpty())
        .andExpect(
            jsonPath("$[?(@.code == 'institution:enrollment-document-request:create')]")
                .isNotEmpty())
        .andExpect(jsonPath("$[?(@.scope == 'PLATFORM')]").isEmpty());
  }

  private void stubPlatformAdminAccess(boolean allowed) {
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(allowed);
  }
}
