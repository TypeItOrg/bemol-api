package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CountCoursesForYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicSpacesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicYearsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListInstrumentsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListShiftsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListStudyPlansUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicYearStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({
  AcademicYearController.class,
  AdminAcademicYearController.class,
  PlatformAcademicCollectionController.class
})
@AutoConfigureMockMvc(addFilters = false)
class AcademicYearControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ACADEMIC_YEAR_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateAcademicYearUseCase createAcademicYearUseCase;
  @MockitoBean private ListAcademicYearsUseCase listAcademicYearsUseCase;
  @MockitoBean private ListAcademicSpacesUseCase listAcademicSpacesUseCase;
  @MockitoBean private ListInstrumentsUseCase listInstrumentsUseCase;
  @MockitoBean private ListShiftsUseCase listShiftsUseCase;
  @MockitoBean private ListStudyPlansUseCase listStudyPlansUseCase;
  @MockitoBean private ListTrainingPathsUseCase listTrainingPathsUseCase;
  @MockitoBean private GetAcademicYearUseCase getAcademicYearUseCase;
  @MockitoBean private UpdateAcademicYearUseCase updateAcademicYearUseCase;
  @MockitoBean private UpdateAcademicYearStatusUseCase updateAcademicYearStatusUseCase;
  @MockitoBean private CountCoursesForYearUseCase countCoursesForYearUseCase;
  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Test
  @DisplayName("Should list academic years across institutions for platform administration")
  void list_usesGlobalPlatformRoute() throws Exception {
    when(listAcademicYearsUseCase.execute(
            isNull(),
            eq("conservatorio"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            any(Pageable.class)))
        .thenReturn(emptyPage());

    mockMvc
        .perform(get("/api/v1/admin/academic-years").param("search", "conservatorio"))
        .andExpect(status().isOk());

    verify(listAcademicYearsUseCase)
        .execute(
            isNull(),
            eq("conservatorio"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            any(Pageable.class));
  }

  @Test
  @DisplayName("Should list academic years under the institution route")
  void list_usesNestedInstitutionRoute() throws Exception {
    when(listAcademicYearsUseCase.execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.<AcademicYearResponse>builder()
                .items(
                    List.of(
                        new AcademicYearResponse(
                            ACADEMIC_YEAR_ID,
                            INSTITUTION_ID,
                            "Conservatorio",
                            2026,
                            null,
                            null,
                            AcademicYearStatus.PLANNED,
                            null)))
                .page(0)
                .size(20)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(get("/api/v1/institutions/{institutionId}/academic-years", INSTITUTION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(ACADEMIC_YEAR_ID.toString()))
        .andExpect(jsonPath("$.items[0].institutionId").value(INSTITUTION_ID.toString()))
        .andExpect(jsonPath("$.items[0].status").value("PLANNED"));

    final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(listAcademicYearsUseCase)
        .execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            pageableCaptor.capture());

    final Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("year");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  @DisplayName("Should list academic years under the platform administration route")
  void list_usesAdminInstitutionRoute() throws Exception {
    when(listAcademicYearsUseCase.execute(
            eq(INSTITUTION_ID),
            eq("2026"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class)))
        .thenReturn(emptyPage());

    mockMvc
        .perform(
            get("/api/v1/admin/institutions/{institutionId}/academic-years", INSTITUTION_ID)
                .param("search", "2026"))
        .andExpect(status().isOk());

    verify(listAcademicYearsUseCase)
        .execute(
            eq(INSTITUTION_ID),
            eq("2026"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class));
  }

  @Test
  @DisplayName("Should pass every academic year filter to the use case")
  void list_passesCombinedFilters() throws Exception {
    final var startDate = LocalDate.of(2026, 3, 1);
    final var endDate = LocalDate.of(2026, 12, 15);
    when(listAcademicYearsUseCase.execute(
            eq(INSTITUTION_ID),
            isNull(),
            eq(AcademicYearStatus.ACTIVE),
            eq(2026),
            eq(startDate),
            eq(endDate),
            any(Pageable.class)))
        .thenReturn(emptyPage());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/academic-years", INSTITUTION_ID)
                .param("status", "ACTIVE")
                .param("year", "2026")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-12-15"))
        .andExpect(status().isOk());

    verify(listAcademicYearsUseCase)
        .execute(
            eq(INSTITUTION_ID),
            isNull(),
            eq(AcademicYearStatus.ACTIVE),
            eq(2026),
            eq(startDate),
            eq(endDate),
            any(Pageable.class));
  }

  @Test
  @DisplayName("Should pass the general search to the institutional academic year route")
  void list_passesSearch() throws Exception {
    when(listAcademicYearsUseCase.execute(
            eq(INSTITUTION_ID),
            eq("activo"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class)))
        .thenReturn(emptyPage());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/academic-years", INSTITUTION_ID)
                .param("search", "activo"))
        .andExpect(status().isOk());

    verify(listAcademicYearsUseCase)
        .execute(
            eq(INSTITUTION_ID),
            eq("activo"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class));
  }

  @Test
  @DisplayName("Should reject an academic year search longer than 100 characters")
  void list_rejectsSearchLongerThanMaximum() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/academic-years", INSTITUTION_ID)
                .param("search", "a".repeat(101)))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest(name = "{0},{1}")
  @CsvSource({"year,asc", "startDate,desc", "endDate,asc"})
  @DisplayName("Should pass supported academic year sort values to the use case")
  void list_passesAcademicYearSort(final String field, final String direction) throws Exception {
    when(listAcademicYearsUseCase.execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class)))
        .thenReturn(emptyPage());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/academic-years", INSTITUTION_ID)
                .param("sort", field + "," + direction))
        .andExpect(status().isOk());

    final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(listAcademicYearsUseCase)
        .execute(
            eq(INSTITUTION_ID),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            pageableCaptor.capture());

    final Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor(field);
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.fromString(direction));
  }

  private static PaginatedResponse<AcademicYearResponse> emptyPage() {
    return PaginatedResponse.<AcademicYearResponse>builder()
        .items(List.of())
        .page(0)
        .size(20)
        .totalItems(0)
        .totalPages(0)
        .build();
  }
}
