package ar.edu.utn.frvm.typeit.boero_api.audit.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventFilter;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.ListAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.config.WebConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(AuditEventController.class)
@Import({
  RoleAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class AuditEventControllerWebMvcTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID SUBJECT_ID = UUID.randomUUID();
  private static final String BASE_PATH = "/api/v1/institutions/{institutionId}/audit-events";

  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListAuditEventsUseCase listAuditEventsUseCase;

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
  @DisplayName("Should list audit events filtered by subject when the caller can read the audit")
  void list_returnsFilteredEvents() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(true);
    when(listAuditEventsUseCase.execute(eq(INSTITUTION_ID), any(AuditEventFilter.class), any()))
        .thenReturn(new PageImpl<>(List.of(event()), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(
            get(BASE_PATH, INSTITUTION_ID)
                .param("subjectPersonId", SUBJECT_ID.toString())
                .param("actedOnBehalf", "true")
                .principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("ENROLLMENT_APPLICATION_SUBMITTED"))
        .andExpect(jsonPath("$.items[0].actedOnBehalf").value(true))
        .andExpect(jsonPath("$.items[0].actorName").value("Tutor Testing"));

    final var captor = org.mockito.ArgumentCaptor.forClass(AuditEventFilter.class);
    verify(listAuditEventsUseCase).execute(eq(INSTITUTION_ID), captor.capture(), any());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().subjectPersonId())
        .isEqualTo(SUBJECT_ID);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().actedOnBehalf()).isTrue();
  }

  @Test
  @DisplayName("Should reject callers without the audit read permission")
  void list_forbiddenWithoutPermission() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_AUDIT_READ)))
        .thenReturn(false);

    mockMvc
        .perform(get(BASE_PATH, INSTITUTION_ID).principal(authentication()))
        .andExpect(status().isForbidden());

    verify(listAuditEventsUseCase, never()).execute(any(), any(), any());
  }

  private AuditEventResponse event() {
    return new AuditEventResponse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Tutor Testing",
        SUBJECT_ID,
        "Martin Crossetin",
        AuditAction.ENROLLMENT_APPLICATION_SUBMITTED,
        AuditEntityType.ENROLLMENT_APPLICATION,
        UUID.randomUUID(),
        true,
        "req-1",
        Instant.parse("2026-09-30T12:00:00Z"));
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
