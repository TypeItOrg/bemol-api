package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.RejectEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ApproveEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.GetEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ListEnrollmentApplicationsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.RejectEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.RequestEnrollmentDocumentsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(InstitutionalEnrollmentApplicationController.class)
@Import({
  InstitutionAccessAspect.class,
  RoleAuthorizationAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class InstitutionalEnrollmentApplicationControllerWebMvcTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID PERSON_ID = UUID.randomUUID();
  private static final UUID STUDY_PLAN_ID = UUID.randomUUID();
  private static final UUID ACADEMIC_YEAR_ID = UUID.randomUUID();
  private static final UUID PERIOD_ID = UUID.randomUUID();
  private static final UUID RESOLVER_PERSON_ID = UUID.randomUUID();

  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListEnrollmentApplicationsUseCase listEnrollmentApplicationsUseCase;
  @MockitoBean private GetEnrollmentApplicationUseCase getEnrollmentApplicationUseCase;
  @MockitoBean private ApproveEnrollmentApplicationUseCase approveEnrollmentApplicationUseCase;
  @MockitoBean private RejectEnrollmentApplicationUseCase rejectEnrollmentApplicationUseCase;
  @MockitoBean private RequestEnrollmentDocumentsUseCase requestEnrollmentDocumentsUseCase;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should list enrollment applications scoped to the institution")
  void list_returnsOkForInstitutionAuthority() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_READ, true);
    when(listEnrollmentApplicationsUseCase.execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            eq(false),
            eq(false),
            isNull(),
            any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(response(EnrollmentApplicationStatus.SUBMITTED)), Pageable.ofSize(20), 1));

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/enrollment-applications", INSTITUTION_ID)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].applicationId").value(APPLICATION_ID.toString()))
        .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"));

    verify(listEnrollmentApplicationsUseCase)
        .execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            eq(false),
            eq(false),
            isNull(),
            any(Pageable.class));
  }

  @Test
  @DisplayName("Should forbid listing without read permission")
  void list_returnsForbiddenWithoutPermission() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_READ, false);

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/enrollment-applications", INSTITUTION_ID)
                .principal(authentication))
        .andExpect(status().isForbidden());

    verify(listEnrollmentApplicationsUseCase, never())
        .execute(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
  }

  @Test
  @DisplayName("Should get a single enrollment application")
  void get_returnsOkForInstitutionAuthority() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_READ, true);
    when(getEnrollmentApplicationUseCase.execute(INSTITUTION_ID, APPLICATION_ID))
        .thenReturn(response(EnrollmentApplicationStatus.REJECTED, "Documentación incompleta"));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/enrollment-applications/{applicationId}",
                    INSTITUTION_ID,
                    APPLICATION_ID)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Documentación incompleta"));
  }

  @Test
  @DisplayName("Should approve a pending application")
  void approve_returnsOkForInstitutionAuthority() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_APPROVE, true);
    when(approveEnrollmentApplicationUseCase.execute(
            INSTITUTION_ID, APPLICATION_ID, RESOLVER_PERSON_ID))
        .thenReturn(response(EnrollmentApplicationStatus.APPROVED));

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/enrollment-applications/{applicationId}/approve",
                    INSTITUTION_ID,
                    APPLICATION_ID)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    verify(approveEnrollmentApplicationUseCase)
        .execute(INSTITUTION_ID, APPLICATION_ID, RESOLVER_PERSON_ID);
  }

  @Test
  @DisplayName("Should forbid approving without approve permission")
  void approve_returnsForbiddenWithoutPermission() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_APPROVE, false);

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/enrollment-applications/{applicationId}/approve",
                    INSTITUTION_ID,
                    APPLICATION_ID)
                .principal(authentication))
        .andExpect(status().isForbidden());

    verify(approveEnrollmentApplicationUseCase, never()).execute(any(), any(), any());
  }

  @Test
  @DisplayName("Should reject an application storing the reason")
  void reject_returnsOkForInstitutionAuthority() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_REJECT, true);
    when(rejectEnrollmentApplicationUseCase.execute(
            eq(INSTITUTION_ID), eq(APPLICATION_ID), any(), eq(RESOLVER_PERSON_ID)))
        .thenReturn(response(EnrollmentApplicationStatus.REJECTED, "Documentación incompleta"));

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/enrollment-applications/{applicationId}/reject",
                    INSTITUTION_ID,
                    APPLICATION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"rejectionReason\":\"Documentación incompleta\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Documentación incompleta"));

    verify(rejectEnrollmentApplicationUseCase)
        .execute(
            eq(INSTITUTION_ID),
            eq(APPLICATION_ID),
            any(RejectEnrollmentApplicationRequest.class),
            eq(RESOLVER_PERSON_ID));
  }

  @Test
  @DisplayName("Should reject a blank rejection reason as invalid input")
  void reject_returnsBadRequestForBlankReason() throws Exception {
    final var authentication = authentication();
    stubPermission(PermissionCode.ENROLLMENT_APPLICATION_REJECT, true);

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/enrollment-applications/{applicationId}/reject",
                    INSTITUTION_ID,
                    APPLICATION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"rejectionReason\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  private Authentication authentication() {
    final var principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(RESOLVER_PERSON_ID)
            .documentNumber("12345678")
            .institutionId(INSTITUTION_ID)
            .sessionId(UUID.randomUUID())
            .tokenId("token-id")
            .build();
    final var auth = new TestingAuthenticationToken(principal, "");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  private void stubPermission(final PermissionCode code, final boolean granted) {
    when(authorizationService.hasPermission(any(Authentication.class), eq(code)))
        .thenReturn(granted);
  }

  private EnrollmentApplicationResponse response(final EnrollmentApplicationStatus status) {
    return response(status, null);
  }

  private EnrollmentApplicationResponse response(
      final EnrollmentApplicationStatus status, final @Nullable String rejectionReason) {
    return EnrollmentApplicationResponse.builder()
        .applicationId(APPLICATION_ID)
        .institutionId(INSTITUTION_ID)
        .personId(PERSON_ID)
        .applicantFirstName("Ana")
        .applicantLastName("Garcia")
        .applicantDocumentNumber("12345678")
        .studyPlanId(STUDY_PLAN_ID)
        .studyPlanName("Profesorado de Música")
        .academicYearId(ACADEMIC_YEAR_ID)
        .academicYear(2026)
        .enrollmentPeriodId(PERIOD_ID)
        .status(status)
        .isEditable(status == EnrollmentApplicationStatus.DRAFT)
        .secondarySchool("Colegio Secundario 1")
        .rejectionReason(rejectionReason)
        .resolvedAt(status == EnrollmentApplicationStatus.SUBMITTED ? null : Instant.now())
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }
}
