package ar.edu.utn.frvm.typeit.bemol_api.audit.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditFeed;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.InstitutionalActivityFilter;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditFilterOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.InstitutionalActivityUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(InstitutionalActivityController.class)
@Import({
  RoleAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class InstitutionalActivityControllerWebMvcTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();

  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private InstitutionalActivityUseCase activity;
  @MockitoBean private AuditFilterOptionsUseCase options;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Autowired private MockMvc mockMvc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void auditOptionsRequireAuditPermissionRatherThanMailPermission() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_EMAIL_READ)))
        .thenReturn(true);
    for (final var path : List.of("modules", "actions")) {
      mockMvc
          .perform(
              get(
                      "/api/v1/institutions/{institutionId}/activity/audit-events/" + path,
                      INSTITUTION_ID)
                  .principal(authentication()))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(options);
  }

  @Test
  void auditOptionsUseDomainDefinitionsWithTheInstitutionalPermission() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(true);
    when(options.institutionalModules())
        .thenReturn(List.of(new AuditModuleDefinition("COURSES", "Cursos")));
    when(options.institutionalActions(eq("COURSES"), eq(null), eq("curso"), any()))
        .thenReturn(Page.empty());
    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/activity/audit-events/modules",
                    INSTITUTION_ID)
                .principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Cursos"));
    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/activity/audit-events/actions",
                    INSTITUTION_ID)
                .param("module", "COURSES")
                .param("search", "curso")
                .principal(authentication()))
        .andExpect(status().isOk());
    verify(options).institutionalActions(eq("COURSES"), eq(null), eq("curso"), any());
  }

  @Test
  void institutionalAuditBindsFiltersButCannotOverrideTheInstitutionOrReadEmails()
      throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(true);
    when(activity.list(eq(INSTITUTION_ID), eq(AuditFeed.ACTIVITY), any(), any()))
        .thenReturn(Page.empty());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/activity/audit-events", INSTITUTION_ID)
                .param("institutionId", UUID.randomUUID().toString())
                .param("scope", "PLATFORM")
                .param("from", "2026-10-01T03:00:00Z")
                .principal(authentication()))
        .andExpect(status().isOk());

    final var filter = ArgumentCaptor.forClass(InstitutionalActivityFilter.class);
    verify(activity).list(eq(INSTITUTION_ID), eq(AuditFeed.ACTIVITY), filter.capture(), any());
    assertThat(filter.getValue().from()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
    assertThat(filter.getValue().scoped(INSTITUTION_ID).institutionId()).isEqualTo(INSTITUTION_ID);
  }

  @Test
  void activityPermissionsAreIndependentForListsAndDetails() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(true);
    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/activity/emails", INSTITUTION_ID)
                .principal(authentication()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/activity/emails/{eventId}",
                    INSTITUTION_ID,
                    UUID.randomUUID())
                .principal(authentication()))
        .andExpect(status().isForbidden());

    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(false);
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_EMAIL_READ)))
        .thenReturn(true);
    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/activity/audit-events", INSTITUTION_ID)
                .principal(authentication()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/activity/audit-events/{eventId}",
                    INSTITUTION_ID,
                    UUID.randomUUID())
                .principal(authentication()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/activity/audit-events/modules",
                    INSTITUTION_ID)
                .principal(authentication()))
        .andExpect(status().isForbidden());
    verifyNoInteractions(activity);
  }

  @Test
  void emailPermissionAllowsTheEmailListWithoutAuditPermission() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_EMAIL_READ)))
        .thenReturn(true);
    when(activity.list(eq(INSTITUTION_ID), eq(AuditFeed.EMAIL), any(), any()))
        .thenReturn(Page.empty());
    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/activity/emails", INSTITUTION_ID)
                .principal(authentication()))
        .andExpect(status().isOk());
    verify(activity).list(eq(INSTITUTION_ID), eq(AuditFeed.EMAIL), any(), any());
  }

  @Test
  void activityRejectsInvalidDatesAndSearchLength() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(true);
    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/activity/audit-events", INSTITUTION_ID)
                .param("from", "not-a-date")
                .principal(authentication()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/activity/audit-events", INSTITUTION_ID)
                .param("search", "x".repeat(101))
                .principal(authentication()))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(activity);
  }

  @Test
  void activityRequiresInstitutionAccessForListsDetailsAndOptions() throws Exception {
    when(authorizationService.hasPermission(any(), any())).thenReturn(true);
    doThrow(new AccessDeniedException("Other institution"))
        .when(institutionalCallerGuard)
        .ensureCallerBelongsToInstitution(any(), eq(INSTITUTION_ID));
    for (final String resource :
        List.of(
            "audit-events",
            "emails",
            "audit-events/modules",
            "audit-events/actions",
            "audit-events/" + UUID.randomUUID(),
            "emails/" + UUID.randomUUID())) {
      mockMvc
          .perform(
              get("/api/v1/institutions/{institutionId}/activity/" + resource, INSTITUTION_ID)
                  .principal(authentication()))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(activity);
  }

  private TestingAuthenticationToken authentication() {
    final var user =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(UUID.randomUUID())
            .documentNumber("35123456")
            .institutionId(INSTITUTION_ID)
            .sessionId(UUID.randomUUID())
            .tokenId("token-id")
            .build();
    final var auth = new TestingAuthenticationToken(user, "");
    auth.setAuthenticated(true);
    return auth;
  }
}
