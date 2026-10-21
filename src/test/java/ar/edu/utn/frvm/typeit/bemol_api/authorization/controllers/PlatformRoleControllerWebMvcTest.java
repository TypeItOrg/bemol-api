package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PlatformRoleListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PlatformRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListPlatformRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(PlatformRoleController.class)
@Import({RoleAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PlatformRoleControllerWebMvcTest {

  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListPlatformRolesUseCase listPlatformRolesUseCase;
  @MockitoBean private InstitutionRoleManagementService roleManagementService;

  @Test
  @DisplayName("Should list roles for a platform administrator")
  void list_returnsRolesForPlatformAdministrator() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(listPlatformRolesUseCase.execute(
            eq("doc"), eq(INSTITUTION_ID), eq(true), any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.<PlatformRoleListItemResponse>builder()
                .items(List.of(roleListItem()))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(
            get("/api/v1/admin/roles")
                .param("search", "doc")
                .param("institutionId", INSTITUTION_ID.toString())
                .param("system", "true")
                .param("size", "10")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(ROLE_ID.toString()))
        .andExpect(jsonPath("$.items[0].name").value("Docentes"))
        .andExpect(jsonPath("$.items[0].permissionCount").value(3));
  }

  @Test
  @DisplayName("Should return role details for a platform administrator")
  void get_returnsRoleDetailsForPlatformAdministrator() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(roleManagementService.getAsPlatformAdmin(ROLE_ID)).thenReturn(roleResponse());

    mockMvc
        .perform(get("/api/v1/admin/roles/{roleId}", ROLE_ID).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Docentes"))
        .andExpect(jsonPath("$.permissions[0]").value("PERSON_READ"))
        .andExpect(jsonPath("$.institution.id").value(INSTITUTION_ID.toString()));
  }

  @Test
  @DisplayName("Should create, update and delete roles for a platform administrator")
  void mutate_allowsPlatformAdministrator() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(roleManagementService.createAsPlatformAdmin(eq(INSTITUTION_ID), any()))
        .thenReturn(institutionRoleResponse());
    when(roleManagementService.updateAsPlatformAdmin(eq(INSTITUTION_ID), eq(ROLE_ID), any()))
        .thenReturn(institutionRoleResponse());

    mockMvc
        .perform(
            post("/api/v1/admin/institutions/{institutionId}/roles", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Docentes\",\"permissions\":[\"institution:person:read-any\"]}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put(
                    "/api/v1/admin/institutions/{institutionId}/roles/{roleId}",
                    INSTITUTION_ID,
                    ROLE_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Docentes actualizados\",\"permissions\":[]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete(
                    "/api/v1/admin/institutions/{institutionId}/roles/{roleId}",
                    INSTITUTION_ID,
                    ROLE_ID)
                .principal(authentication))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Should forbid institutional accounts from global role management")
  void list_forbidsInstitutionalAccounts() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    stubPlatformAdminAccess(false);

    mockMvc
        .perform(get("/api/v1/admin/roles").principal(authentication))
        .andExpect(status().isForbidden());
  }

  private void stubPlatformAdminAccess(boolean allowed) {
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(allowed);
  }

  private static PlatformRoleListItemResponse roleListItem() {
    return PlatformRoleListItemResponse.builder()
        .id(ROLE_ID)
        .name("Docentes")
        .assignmentCount(4)
        .permissionCount(3)
        .institution(
            new PlatformRoleResponse.PlatformRoleInstitution(
                INSTITUTION_ID, "Instituto Boero", true))
        .build();
  }

  private static PlatformRoleResponse roleResponse() {
    return PlatformRoleResponse.builder()
        .id(ROLE_ID)
        .name("Docentes")
        .assignmentCount(4)
        .permissions(Set.of("PERSON_READ"))
        .protectedPermissions(Set.of())
        .institution(
            new PlatformRoleResponse.PlatformRoleInstitution(
                INSTITUTION_ID, "Instituto Boero", true))
        .build();
  }

  private static InstitutionRoleResponse institutionRoleResponse() {
    return InstitutionRoleResponse.builder()
        .id(ROLE_ID)
        .name("Docentes")
        .permissions(Set.of("PERSON_READ"))
        .protectedPermissions(Set.of())
        .build();
  }
}
