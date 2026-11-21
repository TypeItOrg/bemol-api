package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeStudentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PublishGradesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeCommands.DeleteOutcome;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.TeacherCourseGradeService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({
  CourseEnrollmentGradeController.class,
  CourseClassGradeController.class,
  PendingClassGradesController.class,
  TeacherCourseGradeController.class,
  OwnCourseEnrollmentGradeController.class
})
@Import({
  PermissionAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  InstitutionalCallerGuard.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class CourseEnrollmentGradeControllersWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CLASS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ENROLLMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID GRADE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("66666666-6666-6666-6666-666666666666");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private AcademicAccessGuard accessGuard;
  @MockitoBean private OperatingContextResolver operatingContext;
  @MockitoBean private CourseEnrollmentGradeService gradeService;
  @MockitoBean private TeacherCourseGradeService teacherGradeService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("pendingSummary requires COURSE_ENROLLMENT_GRADE_PUBLISH permission")
  void pendingSummary_forbidsUserWithoutPublishPermission() throws Exception {
    final var auth = bind(institutionalPrincipal(USER_ID, INSTITUTION_ID));
    when(authorizationService.hasPermission(
            any(), eq(PermissionCode.COURSE_ENROLLMENT_GRADE_PUBLISH)))
        .thenReturn(false);

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/course-classes/{classId}/grades/pending-summary",
                    INSTITUTION_ID,
                    CLASS_ID)
                .principal(auth))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("pendingSummary returns summary when caller has publish permission")
  void pendingSummary_allowsPublisher() throws Exception {
    final var auth = bind(institutionalPrincipal(USER_ID, INSTITUTION_ID));
    when(authorizationService.hasPermission(
            any(), eq(PermissionCode.COURSE_ENROLLMENT_GRADE_PUBLISH)))
        .thenReturn(true);
    when(gradeService.pendingSummary(INSTITUTION_ID, CLASS_ID))
        .thenReturn(new PendingGradesSummaryResponse(2, 1, 1, 1, 0));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/course-classes/{classId}/grades/pending-summary",
                    INSTITUTION_ID,
                    CLASS_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pendingChanges").value(2))
        .andExpect(jsonPath("$.affectedStudents").value(1));
  }

  @Test
  @DisplayName("Platform admin can create and publish grades with nullable actor person id")
  void platformAdmin_canCreateAndPublish() throws Exception {
    final var auth = bind(platformPrincipal(PLATFORM_ACCOUNT_ID));
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(true);
    when(authorizationService.hasPermission(any(), any())).thenReturn(true);
    final var created = sampleGradeResponse();
    when(gradeService.create(
            eq(INSTITUTION_ID),
            eq(ENROLLMENT_ID),
            eq("Parcial 1"),
            eq(new BigDecimal("8.5")),
            isNull()))
        .thenReturn(created);
    when(gradeService.publishClass(eq(INSTITUTION_ID), eq(CLASS_ID), isNull()))
        .thenReturn(new PublishGradesResponse(1, 0, 1));

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/course-enrollments/{enrollmentId}/grades",
                    INSTITUTION_ID,
                    ENROLLMENT_ID)
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evaluation\":\"Parcial 1\",\"value\":8.5}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(GRADE_ID.toString()))
        .andExpect(jsonPath("$.version").value(0));

    mockMvc
        .perform(
            post(
                    "/api/v1/institutions/{institutionId}/course-classes/{classId}/grades/publish",
                    INSTITUTION_ID,
                    CLASS_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedGrades").value(1));
  }

  @Test
  @DisplayName("Delete returns 204 for immediate draft deletion and 200 for marked deletion")
  void delete_returns204Or200() throws Exception {
    final var principal = institutionalPrincipal(USER_ID, INSTITUTION_ID);
    final var auth = bind(principal);
    when(authorizationService.hasPermission(
            any(), eq(PermissionCode.COURSE_ENROLLMENT_GRADE_DELETE)))
        .thenReturn(true);
    when(gradeService.delete(
            INSTITUTION_ID, ENROLLMENT_ID, GRADE_ID, 0L, principal.actorPersonId()))
        .thenReturn(DeleteOutcome.DeletedImmediately.INSTANCE);

    mockMvc
        .perform(
            delete(
                    "/api/v1/institutions/{institutionId}/course-enrollments/{enrollmentId}/grades/{gradeId}",
                    INSTITUTION_ID,
                    ENROLLMENT_ID,
                    GRADE_ID)
                .param("expectedVersion", "0")
                .principal(auth))
        .andExpect(status().isNoContent());

    when(gradeService.delete(
            INSTITUTION_ID, ENROLLMENT_ID, GRADE_ID, 1L, principal.actorPersonId()))
        .thenReturn(new DeleteOutcome.MarkedForDeletion(sampleGradeResponse()));

    mockMvc
        .perform(
            delete(
                    "/api/v1/institutions/{institutionId}/course-enrollments/{enrollmentId}/grades/{gradeId}",
                    INSTITUTION_ID,
                    ENROLLMENT_ID,
                    GRADE_ID)
                .param("expectedVersion", "1")
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(GRADE_ID.toString()));
  }

  @Test
  @DisplayName("History returns PaginatedResponse shape")
  void history_returnsPaginatedResponse() throws Exception {
    final var auth = bind(institutionalPrincipal(USER_ID, INSTITUTION_ID));
    when(authorizationService.hasPermission(any(), eq(PermissionCode.COURSE_ENROLLMENT_GRADE_READ)))
        .thenReturn(true);
    final var event =
        new CourseEnrollmentGradeHistoryResponse(
            UUID.randomUUID(),
            GRADE_ID,
            CourseEnrollmentGradeHistoryOperation.CREATED,
            null,
            null,
            "Parcial 1",
            new BigDecimal("8.5"),
            null,
            null,
            null,
            null,
            null,
            CourseEnrollmentGradePublicationStatus.DRAFT,
            null,
            Instant.parse("2026-10-15T12:00:00Z"));
    when(gradeService.history(eq(INSTITUTION_ID), eq(ENROLLMENT_ID), eq(GRADE_ID), any()))
        .thenReturn(new PaginatedResponse<>(List.of(event), 0, 20, 1, 1));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/course-enrollments/{enrollmentId}/grades/{gradeId}/history",
                    INSTITUTION_ID,
                    ENROLLMENT_ID,
                    GRADE_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  @DisplayName("OwnCourseEnrollmentGradeController enforces operatingContext.requireStudent")
  void ownGrades_enforcesOperatingContext() throws Exception {
    final var principal = institutionalPrincipal(USER_ID, INSTITUTION_ID);
    final var auth = bind(principal);
    when(gradeService.listOwnPublished(INSTITUTION_ID, ENROLLMENT_ID, principal.personId()))
        .thenReturn(
            List.of(
                new CourseEnrollmentGradeStudentResponse(
                    GRADE_ID, "Parcial 1", new BigDecimal("8.5"))));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/course-enrollments/mine/{enrollmentId}/grades",
                    INSTITUTION_ID,
                    ENROLLMENT_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].evaluation").value("Parcial 1"));

    verify(operatingContext).requireStudent(INSTITUTION_ID, principal.personId(), principal);

    doThrow(new UnauthorizedGuardianshipException())
        .when(operatingContext)
        .requireStudent(INSTITUTION_ID, principal.personId(), principal);

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/course-enrollments/mine/{enrollmentId}/grades",
                    INSTITUTION_ID,
                    ENROLLMENT_ID)
                .principal(auth))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("TeacherCourseGradeController returns 403 when unassigned or missing capability")
  void teacherGrades_returnsForbiddenWhenDenied() throws Exception {
    final var principal = institutionalPrincipal(USER_ID, INSTITUTION_ID);
    final var auth = bind(principal);
    when(teacherGradeService.list(INSTITUTION_ID, principal.personId(), CLASS_ID, ENROLLMENT_ID))
        .thenThrow(new AccessDeniedException("Forbidden"));

    mockMvc
        .perform(
            get(
                    "/api/v1/institutions/{institutionId}/teacher/classes/{classId}/enrollments/{enrollmentId}/grades",
                    INSTITUTION_ID,
                    CLASS_ID,
                    ENROLLMENT_ID)
                .principal(auth))
        .andExpect(status().isForbidden());
  }

  private static Authentication bind(final Object principal) {
    final var auth = new TestingAuthenticationToken(principal, "");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  private static CourseEnrollmentGradeResponse sampleGradeResponse() {
    final Instant now = Instant.parse("2026-10-15T12:00:00Z");
    return new CourseEnrollmentGradeResponse(
        GRADE_ID,
        "Parcial 1",
        new BigDecimal("8.5"),
        CourseEnrollmentGradePublicationStatus.DRAFT,
        null,
        null,
        null,
        now,
        null,
        now,
        null,
        null,
        0L);
  }
}
