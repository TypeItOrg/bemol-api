package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
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
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.GetMyEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ListMyEnrollmentApplicationsUseCase;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(MyEnrollmentApplicationController.class)
@Import({
  RoleAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class MyEnrollmentApplicationControllerWebMvcTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID PERSON_ID = UUID.randomUUID();
  private static final UUID STUDY_PLAN_ID = UUID.randomUUID();
  private static final UUID ACADEMIC_YEAR_ID = UUID.randomUUID();

  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListMyEnrollmentApplicationsUseCase listMyEnrollmentApplicationsUseCase;
  @MockitoBean private GetMyEnrollmentApplicationUseCase getMyEnrollmentApplicationUseCase;

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
  @DisplayName("Should list my enrollment applications")
  void list_returnsApplicationsForApplicant() throws Exception {
    final var auth = authentication();
    when(listMyEnrollmentApplicationsUseCase.execute(
            eq(INSTITUTION_ID), eq(PERSON_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(response(EnrollmentApplicationStatus.SUBMITTED)), Pageable.ofSize(20), 1));

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/my-enrollment-applications", INSTITUTION_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].applicationId").value(APPLICATION_ID.toString()))
        .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"));

    verify(listMyEnrollmentApplicationsUseCase)
        .execute(eq(INSTITUTION_ID), eq(PERSON_ID), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  @DisplayName("Should get my single enrollment application")
  void get_returnsApplicationForApplicant() throws Exception {
    final var auth = authentication();
    when(getMyEnrollmentApplicationUseCase.execute(INSTITUTION_ID, PERSON_ID, APPLICATION_ID))
        .thenReturn(response(EnrollmentApplicationStatus.REJECTED, "Motivo de prueba"));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/my-enrollment-applications/{applicationId}",
                    INSTITUTION_ID,
                    APPLICATION_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Motivo de prueba"));

    verify(getMyEnrollmentApplicationUseCase).execute(INSTITUTION_ID, PERSON_ID, APPLICATION_ID);
  }

  private TestingAuthenticationToken authentication() {
    final var user =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(PERSON_ID)
            .documentNumber("12345678")
            .institutionId(INSTITUTION_ID)
            .sessionId(UUID.randomUUID())
            .tokenId("token-id")
            .build();
    final var auth = new TestingAuthenticationToken(user, "");
    auth.setAuthenticated(true);
    return auth;
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
        .enrollmentPeriodId(UUID.randomUUID())
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
