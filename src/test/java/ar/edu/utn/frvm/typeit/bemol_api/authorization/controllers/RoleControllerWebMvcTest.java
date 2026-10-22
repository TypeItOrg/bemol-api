package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.SystemRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.BootstrapInstitutionalAuthorityUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListPersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListSystemRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.QueryInstitutionRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ReplacePersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.RevokePersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({RoleController.class, AdminRoleController.class, InstitutionRoleController.class})
@Import({
  RoleAuthorizationAspect.class,
  PermissionAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  InstitutionalCallerGuard.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class RoleControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_INSTITUTION_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID ROLE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
  private static final UUID USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
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
  @MockitoBean private ListPersonRolesUseCase listPersonRolesUseCase;
  @MockitoBean private AssignPersonRoleUseCase assignPersonRoleUseCase;
  @MockitoBean private RevokePersonRoleUseCase revokePersonRoleUseCase;
  @MockitoBean private ReplacePersonRolesUseCase replacePersonRolesUseCase;
  @MockitoBean private ListSystemRolesUseCase listSystemRolesUseCase;
  @MockitoBean private InstitutionRoleManagementService institutionRoleManagementService;
  @MockitoBean private QueryInstitutionRolesUseCase roleQueries;

  @MockitoBean
  private BootstrapInstitutionalAuthorityUseCase bootstrapInstitutionalAuthorityUseCase;

  @Test
  @DisplayName("Should list paginated institutional roles for an authorized caller")
  void listInstitutionRoles_returnsPaginatedRolesForAuthorizedCaller() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubAnyRolePermission(true);
    when(institutionRoleManagementService.list(eq(INSTITUTION_ID), eq("doc"), any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.<InstitutionRoleResponse>builder()
                .items(List.of(institutionRoleResponse()))
                .page(1)
                .size(20)
                .totalItems(21)
                .totalPages(2)
                .build());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/roles", INSTITUTION_ID)
                .param("search", "doc")
                .param("page", "1")
                .param("size", "20")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(ROLE_ID.toString()))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalItems").value(21))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  @DisplayName("Should forbid unauthenticated access to person roles")
  void listPersonRoles_returnsForbiddenWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            get(
                "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                INSTITUTION_ID,
                PERSON_ID))
        .andExpect(status().isForbidden());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should list person roles for institutional authority")
  void listPersonRoles_returnsRolesForAuthorizedCaller() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN, true);
    stubAnyRolePermission(true);
    when(listPersonRolesUseCase.execute(INSTITUTION_ID, PERSON_ID))
        .thenReturn(List.of(personRoleResponse(SystemRoleCode.TEACHER)));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].technicalCode").value("TEACHER"))
        .andExpect(jsonPath("$[0].assignedAt").value("2026-01-15T10:00:00Z"));
  }

  @Test
  @DisplayName("Should forbid listing roles from another institution")
  void listPersonRoles_returnsForbiddenForDifferentInstitution() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN, true);
    stubAnyRolePermission(true);

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                    OTHER_INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isForbidden());

    verify(listPersonRolesUseCase, org.mockito.Mockito.never()).execute(any(), any());
  }

  @Test
  @DisplayName(
      "Should forbid institutional principal without assign permission from assigning roles")
  void assignPersonRole_returnsForbiddenWithoutPermission() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN, false);

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"roleId\":\""
                        + ROLE_ID
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should assign role for institutional authority")
  void assignPersonRole_returnsCreatedForAuthorizedCaller() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN, true);
    stubAnyRolePermission(true);
    when(assignPersonRoleUseCase.execute(
            eq(INSTITUTION_ID), eq(PERSON_ID), any(AssignRoleRequest.class), eq(false)))
        .thenReturn(personRoleResponse(SystemRoleCode.TEACHER));

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"roleId\":\""
                        + ROLE_ID
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.technicalCode").value("TEACHER"));
  }

  @Test
  @DisplayName("Should revoke role for institutional authority with revoke permission")
  void revokePersonRole_returnsNoContentForAuthorizedCaller() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPermission(PermissionCode.INSTITUTION_ROLE_REVOKE, true);

    mockMvc
        .perform(
            delete(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles/{roleId}",
                    INSTITUTION_ID,
                    PERSON_ID,
                    ROLE_ID)
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(revokePersonRoleUseCase).execute(INSTITUTION_ID, PERSON_ID, ROLE_ID, false);
  }

  @Test
  @DisplayName("Should list system roles for institutional authority")
  void listSystemRoles_returnsCatalogForAuthorizedCaller() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN, true);
    stubAnyRolePermission(true);
    when(listSystemRolesUseCase.execute())
        .thenReturn(
            List.of(
                SystemRoleResponse.builder()
                    .code(SystemRoleCode.TEACHER)
                    .displayName("Docente")
                    .build()));

    mockMvc
        .perform(get("/api/v1/roles/system").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0].code").value("TEACHER"));
  }

  @Test
  @DisplayName("Should bootstrap institutional authority for platform admin")
  void bootstrapInstitutionalAuthority_returnsCreatedForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(bootstrapInstitutionalAuthorityUseCase.execute(INSTITUTION_ID, PERSON_ID))
        .thenReturn(personRoleResponse(SystemRoleCode.INSTITUTIONAL_AUTHORITY));

    mockMvc
        .perform(
            post(
                    "/api/v1/admin/institutions/{institutionId}/authority/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.technicalCode").value("INSTITUTIONAL_AUTHORITY"));
  }

  @Test
  @DisplayName("Should forbid institutional principal from bootstrap endpoint")
  void bootstrapInstitutionalAuthority_returnsForbiddenForInstitutionalPrincipal()
      throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    stubPlatformAdminAccess(false);

    mockMvc
        .perform(
            post(
                    "/api/v1/admin/institutions/{institutionId}/authority/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should allow platform admin to list roles for any institution")
  void listPersonRoles_returnsRolesForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(listPersonRolesUseCase.execute(INSTITUTION_ID, PERSON_ID))
        .thenReturn(List.of(personRoleResponse(SystemRoleCode.TEACHER)));

    mockMvc
        .perform(
            get(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].technicalCode").value("TEACHER"));
  }

  @Test
  @DisplayName("Should allow platform admin to assign roles for any institution")
  void assignPersonRole_returnsCreatedForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(assignPersonRoleUseCase.execute(
            eq(INSTITUTION_ID), eq(PERSON_ID), any(AssignRoleRequest.class), eq(true)))
        .thenReturn(personRoleResponse(SystemRoleCode.TEACHER));

    mockMvc
        .perform(
            post(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"roleId\":\""
                        + ROLE_ID
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.technicalCode").value("TEACHER"));
  }

  @Test
  @DisplayName("Should allow platform admin to revoke roles for any institution")
  void revokePersonRole_returnsNoContentForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);

    mockMvc
        .perform(
            delete(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}/roles/{roleId}",
                    INSTITUTION_ID,
                    PERSON_ID,
                    ROLE_ID)
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(revokePersonRoleUseCase).execute(INSTITUTION_ID, PERSON_ID, ROLE_ID, true);
  }

  @Test
  @DisplayName("Should allow platform admin to list system roles through admin endpoint")
  void listSystemRoles_returnsCatalogForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(listSystemRolesUseCase.execute())
        .thenReturn(
            List.of(
                SystemRoleResponse.builder()
                    .code(SystemRoleCode.TEACHER)
                    .displayName("Docente")
                    .build()));

    mockMvc
        .perform(get("/api/v1/admin/roles/system").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0].code").value("TEACHER"));
  }

  private void stubPermission(PermissionCode permission, boolean allowed) {
    when(authorizationService.hasPermission(any(), eq(permission))).thenReturn(allowed);
  }

  @Test
  void rejectsLegacyRoleWriteWithoutExplicitScope() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"roleId\":\"" + ROLE_ID + "\"}"))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(assignPersonRoleUseCase);
  }

  @Test
  void rejectsDuplicatedTrainingPathsBeforeCallingTheUseCase() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/people/{personId}/roles",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"roleId\":\""
                        + ROLE_ID
                        + "\",\"accessScope\":\"TRAINING_PATHS\",\"trainingPathIds\":[\""
                        + INSTITUTION_ID
                        + "\",\""
                        + INSTITUTION_ID
                        + "\"]}"))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(assignPersonRoleUseCase);
  }

  private void stubAnyRolePermission(boolean allowed) {
    when(authorizationService.hasAnyPermission(any(), any(PermissionCode[].class)))
        .thenReturn(allowed);
  }

  private void stubPlatformAdminAccess(boolean allowed) {
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(allowed);
  }

  private static PersonRoleResponse personRoleResponse(SystemRoleCode roleCode) {
    return PersonRoleResponse.builder()
        .roleId(ROLE_ID)
        .technicalCode(roleCode)
        .displayName(roleCode.getDisplayName())
        .assignedAt(Instant.parse("2026-01-15T10:00:00Z"))
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
