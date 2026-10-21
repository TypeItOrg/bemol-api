package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateCourseUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetCourseUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCourseLevelOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCourseSpaceOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCourseTeachersUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCoursesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ReplaceCourseClassesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateCourseStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({
  CourseController.class,
  AdminCourseController.class,
  PlatformCourseCollectionController.class
})
@AutoConfigureMockMvc(addFilters = false)
class CourseControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID COURSE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateCourseUseCase createCourseUseCase;
  @MockitoBean private ListCoursesUseCase listCoursesUseCase;
  @MockitoBean private ListCourseLevelOptionsUseCase listCourseLevelOptionsUseCase;
  @MockitoBean private GetCourseUseCase getCourseUseCase;
  @MockitoBean private UpdateCourseStatusUseCase updateCourseStatusUseCase;
  @MockitoBean private ReplaceCourseClassesUseCase replaceCourseClassesUseCase;
  @MockitoBean private ListCourseTeachersUseCase listCourseTeachersUseCase;
  @MockitoBean private ListCourseSpaceOptionsUseCase listCourseSpaceOptionsUseCase;
  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Test
  @DisplayName("Should create a course through the institutional route")
  void create_usesInstitutionalRoute() throws Exception {
    final String body =
        """
        {
          "studyPlanId": "%s",
          "academicSpaceId": "%s",
          "academicYearId": "%s",
          "classes": [
            {
              "teacherIds": ["%s"],
              "days": [
                {
                  "dayOfWeek": "MONDAY",
                  "capacity": null,
                  "periodDurationMinutes": 60,
                  "schedules": [{"startTime": "14:00:00", "endTime": "18:00:00"}]
                }
              ]
            }
          ]
        }
        """
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/institutions/" + INSTITUTION_ID + "/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    verify(createCourseUseCase).execute(eq(INSTITUTION_ID), any());
  }

  @Test
  @DisplayName("Should pass course filters and default sort through the institutional route")
  void list_passesFiltersAndDefaultSort() throws Exception {
    when(listCoursesUseCase.execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            eq(false),
            any(Pageable.class)))
        .thenReturn(new PaginatedResponse<>(List.of(), 0, 10, 0, 0));

    mockMvc
        .perform(get("/api/v1/institutions/" + INSTITUTION_ID + "/courses").param("year", "2027"))
        .andExpect(status().isOk());

    final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(listCoursesUseCase)
        .execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(2027),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            eq(false),
            pageable.capture());
    assertThatSortIs(pageable.getValue(), "academicSpace.name");
  }

  @Test
  @DisplayName("Should expose the platform global collection route")
  void list_usesGlobalPlatformRoute() throws Exception {
    when(listCoursesUseCase.execute(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            eq(false),
            any(Pageable.class)))
        .thenReturn(new PaginatedResponse<>(List.of(), 0, 10, 0, 0));

    mockMvc.perform(get("/api/v1/admin/courses")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should replace the classes of a course")
  void put_replacesClasses() throws Exception {
    final String body =
        """
        {"classes": [{"teacherIds": ["%s"], "days": [{"dayOfWeek": "FRIDAY", "capacity": 20, "periodDurationMinutes": null,
          "schedules": [{"startTime": "08:00:00", "endTime": "12:00:00"}]}]}]}
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            put("/api/v1/institutions/" + INSTITUTION_ID + "/courses/" + COURSE_ID + "/classes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    verify(replaceCourseClassesUseCase).execute(eq(INSTITUTION_ID), eq(COURSE_ID), any());
  }

  @Test
  @DisplayName("Should update the status of a course without content")
  void patch_updatesStatus() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/institutions/" + INSTITUTION_ID + "/courses/" + COURSE_ID + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"INACTIVE\"}"))
        .andExpect(status().isNoContent());

    verify(updateCourseStatusUseCase)
        .execute(eq(INSTITUTION_ID), eq(COURSE_ID), any(CourseStatusRequest.class));
  }

  @Test
  @DisplayName("Should reject an unknown body when creating a course")
  void create_rejectsMissingClasses() throws Exception {
    final String body =
        """
        {"studyPlanId": "%s", "academicSpaceId": "%s", "academicYearId": "%s"}
        """
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/institutions/" + INSTITUTION_ID + "/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.classes").exists());
  }

  @Test
  @DisplayName("Should reject null elements in nested course classes")
  void create_rejectsNullClassElement() throws Exception {
    final String body =
        """
        {"studyPlanId": "%s", "academicSpaceId": "%s", "academicYearId": "%s", "classes": [null]}
        """
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/institutions/" + INSTITUTION_ID + "/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject null elements at every nested course level")
  void create_rejectsNullNestedElements() throws Exception {
    final String body =
        """
        {
          "studyPlanId": "%s",
          "academicSpaceId": "%s",
          "academicYearId": "%s",
          "classes": [{
            "teacherIds": [null],
            "days": [null, {
              "dayOfWeek": "MONDAY",
              "capacity": 10,
              "periodDurationMinutes": null,
              "schedules": [null]
            }]
          }]
        }
        """
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/institutions/" + INSTITUTION_ID + "/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  private static void assertThatSortIs(final Pageable pageable, final String property) {
    assertThat(pageable.getSort().getOrderFor(property))
        .isNotNull()
        .extracting(mappedOrder -> mappedOrder.getDirection())
        .isEqualTo(Sort.Direction.ASC);
  }
}
