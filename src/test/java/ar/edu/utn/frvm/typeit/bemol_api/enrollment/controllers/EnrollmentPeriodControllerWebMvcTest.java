package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CreateEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.DeleteEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.GetEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentPeriodsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.UpdateEnrollmentPeriodStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.UpdateEnrollmentPeriodUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(EnrollmentPeriodController.class)
@Import({PermissionAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class EnrollmentPeriodControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PERIOD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ACADEMIC_YEAR_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;

  @MockitoBean private CreateEnrollmentPeriodUseCase createEnrollmentPeriodUseCase;
  @MockitoBean private ListEnrollmentPeriodsUseCase listEnrollmentPeriodsUseCase;
  @MockitoBean private GetEnrollmentPeriodUseCase getEnrollmentPeriodUseCase;
  @MockitoBean private UpdateEnrollmentPeriodUseCase updateEnrollmentPeriodUseCase;
  @MockitoBean private UpdateEnrollmentPeriodStatusUseCase updateEnrollmentPeriodStatusUseCase;
  @MockitoBean private DeleteEnrollmentPeriodUseCase deleteEnrollmentPeriodUseCase;

  @Test
  @DisplayName("Should create an enrollment period when authorized")
  void create_success() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.ENROLLMENT_PERIOD_CREATE)))
        .thenReturn(true);

    var response =
        new EnrollmentPeriodResponse(
            PERIOD_ID,
            INSTITUTION_ID,
            ACADEMIC_YEAR_ID,
            2026,
            "Inscripción 2026",
            LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 12, 1, 20, 0).toInstant(ZoneOffset.UTC),
            EnrollmentPeriodStatus.PLANNED,
            null,
            false,
            List.of(),
            false,
            true,
            true,
            true);

    when(createEnrollmentPeriodUseCase.execute(eq(INSTITUTION_ID), any())).thenReturn(response);

    var body =
        Map.of(
            "academicYearId", ACADEMIC_YEAR_ID,
            "name", "Inscripción 2026",
            "startDate", "2026-11-01T08:00:00Z",
            "endDate", "2026-12-01T20:00:00Z",
            "offerings",
                List.of(
                    Map.of(
                        "studyPlanId",
                        UUID.randomUUID(),
                        "academicLevelIds",
                        List.of(),
                        "includeUnassigned",
                        true)));

    mockMvc
        .perform(
            post("/api/v1/institutions/{institutionId}/enrollment-periods", INSTITUTION_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(PERIOD_ID.toString()))
        .andExpect(jsonPath("$.name").value("Inscripción 2026"));
  }

  @Test
  @DisplayName("Should forbid creation when user lacks permission")
  void create_forbidden() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.ENROLLMENT_PERIOD_CREATE)))
        .thenReturn(false);

    var body =
        Map.of(
            "academicYearId", ACADEMIC_YEAR_ID,
            "name", "Inscripción 2026",
            "startDate", "2026-11-01T08:00:00Z",
            "endDate", "2026-12-01T20:00:00Z",
            "offerings",
                List.of(
                    Map.of(
                        "studyPlanId",
                        UUID.randomUUID(),
                        "academicLevelIds",
                        List.of(),
                        "includeUnassigned",
                        true)));

    mockMvc
        .perform(
            post("/api/v1/institutions/{institutionId}/enrollment-periods", INSTITUTION_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should change status when authorized")
  void updateStatus_success() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(
            any(), eq(PermissionCode.ENROLLMENT_PERIOD_STATUS_UPDATE)))
        .thenReturn(true);

    var body = Map.of("status", "OPEN");

    mockMvc
        .perform(
            patch(
                    "/api/v1/institutions/{institutionId}/enrollment-periods/{periodId}/status",
                    INSTITUTION_ID,
                    PERIOD_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNoContent());

    verify(updateEnrollmentPeriodStatusUseCase).execute(eq(INSTITUTION_ID), eq(PERIOD_ID), any());
  }

  @Test
  @DisplayName("Should delete period when authorized")
  void delete_success() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.ENROLLMENT_PERIOD_DELETE)))
        .thenReturn(true);

    mockMvc
        .perform(
            delete(
                    "/api/v1/institutions/{institutionId}/enrollment-periods/{periodId}",
                    INSTITUTION_ID,
                    PERIOD_ID)
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(deleteEnrollmentPeriodUseCase).execute(INSTITUTION_ID, PERIOD_ID);
  }
}
