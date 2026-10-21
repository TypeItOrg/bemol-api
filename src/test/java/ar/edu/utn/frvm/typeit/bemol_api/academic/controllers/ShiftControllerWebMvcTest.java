package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.AcademicLifecycleService;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicSpaceUsageUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicSpacesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListInstrumentsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListShiftsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftUseCase;
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
  AcademicCatalogController.class,
  AdminAcademicCatalogController.class,
  AcademicLifecycleController.class,
  AdminAcademicLifecycleController.class
})
@AutoConfigureMockMvc(addFilters = false)
class ShiftControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SHIFT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateAcademicSpaceUseCase createAcademicSpaceUseCase;
  @MockitoBean private ListAcademicSpacesUseCase listAcademicSpacesUseCase;
  @MockitoBean private GetAcademicSpaceUseCase getAcademicSpaceUseCase;
  @MockitoBean private GetAcademicSpaceUsageUseCase getAcademicSpaceUsageUseCase;
  @MockitoBean private UpdateAcademicSpaceUseCase updateAcademicSpaceUseCase;
  @MockitoBean private UpdateAcademicSpaceStatusUseCase updateAcademicSpaceStatusUseCase;
  @MockitoBean private CreateInstrumentUseCase createInstrumentUseCase;
  @MockitoBean private ListInstrumentsUseCase listInstrumentsUseCase;
  @MockitoBean private GetInstrumentUseCase getInstrumentUseCase;
  @MockitoBean private UpdateInstrumentUseCase updateInstrumentUseCase;
  @MockitoBean private UpdateInstrumentStatusUseCase updateInstrumentStatusUseCase;
  @MockitoBean private CreateShiftUseCase createShiftUseCase;
  @MockitoBean private ListShiftsUseCase listShiftsUseCase;
  @MockitoBean private GetShiftUseCase getShiftUseCase;
  @MockitoBean private UpdateShiftUseCase updateShiftUseCase;
  @MockitoBean private UpdateShiftStatusUseCase updateShiftStatusUseCase;
  @MockitoBean private AcademicLifecycleService lifecycleService;
  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Test
  @DisplayName("Should create a shift under the institution route")
  void create_returnsCreatedShift() throws Exception {
    when(createShiftUseCase.execute(eq(INSTITUTION_ID), any()))
        .thenReturn(
            new ShiftResponse(
                SHIFT_ID,
                INSTITUTION_ID,
                "Institución de prueba",
                "Turno mañana",
                null,
                true,
                null));

    mockMvc
        .perform(
            post("/api/v1/institutions/{institutionId}/shifts", INSTITUTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno mañana\",\"description\":null}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(SHIFT_ID.toString()))
        .andExpect(jsonPath("$.institutionId").value(INSTITUTION_ID.toString()))
        .andExpect(jsonPath("$.name").value("Turno mañana"))
        .andExpect(jsonPath("$.active").value(true));

    verify(createShiftUseCase).execute(eq(INSTITUTION_ID), any());
  }

  @Test
  @DisplayName("Should reject creating a shift without a name")
  void create_rejectsBlankName() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/institutions/{institutionId}/shifts", INSTITUTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \",\"description\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should list shifts with a default name sort under the institution route")
  void list_usesNestedInstitutionRouteWithDefaultNameSort() throws Exception {
    when(listShiftsUseCase.execute(
            eq(INSTITUTION_ID), isNull(), isNull(), eq(false), any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.<ShiftResponse>builder()
                .items(
                    List.of(
                        new ShiftResponse(
                            SHIFT_ID,
                            INSTITUTION_ID,
                            "Institución de prueba",
                            "Turno mañana",
                            null,
                            true,
                            null)))
                .page(0)
                .size(20)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(get("/api/v1/institutions/{institutionId}/shifts", INSTITUTION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(SHIFT_ID.toString()));

    final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(listShiftsUseCase)
        .execute(eq(INSTITUTION_ID), isNull(), isNull(), eq(false), pageableCaptor.capture());

    final Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("name");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  @DisplayName("Should pass search and status filters to the list use case")
  void list_passesCombinedFilters() throws Exception {
    when(listShiftsUseCase.execute(
            eq(INSTITUTION_ID), eq("mañana"), eq(true), eq(false), any(Pageable.class)))
        .thenReturn(emptyPage());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/shifts", INSTITUTION_ID)
                .param("search", "mañana")
                .param("active", "true"))
        .andExpect(status().isOk());

    verify(listShiftsUseCase)
        .execute(eq(INSTITUTION_ID), eq("mañana"), eq(true), eq(false), any(Pageable.class));
  }

  @Test
  @DisplayName("Should return a single shift by id")
  void get_returnsShiftById() throws Exception {
    when(getShiftUseCase.execute(INSTITUTION_ID, SHIFT_ID))
        .thenReturn(
            new ShiftResponse(
                SHIFT_ID,
                INSTITUTION_ID,
                "Institución de prueba",
                "Turno tarde",
                null,
                false,
                null));

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/shifts/{shiftId}", INSTITUTION_ID, SHIFT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Turno tarde"))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @DisplayName("Should update a shift")
  void update_returnsUpdatedShift() throws Exception {
    when(updateShiftUseCase.execute(eq(INSTITUTION_ID), eq(SHIFT_ID), any()))
        .thenReturn(
            new ShiftResponse(
                SHIFT_ID,
                INSTITUTION_ID,
                "Institución de prueba",
                "Turno noche",
                "De 20 a 24",
                true,
                null));

    mockMvc
        .perform(
            put("/api/v1/institutions/{institutionId}/shifts/{shiftId}", INSTITUTION_ID, SHIFT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno noche\",\"description\":\"De 20 a 24\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Turno noche"))
        .andExpect(jsonPath("$.description").value("De 20 a 24"));
  }

  @Test
  @DisplayName("Should change the status of a shift returning no content")
  void updateStatus_returnsNoContent() throws Exception {
    mockMvc
        .perform(
            patch(
                    "/api/v1/institutions/{institutionId}/shifts/{shiftId}/status",
                    INSTITUTION_ID,
                    SHIFT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isNoContent());

    verify(updateShiftStatusUseCase).execute(eq(INSTITUTION_ID), eq(SHIFT_ID), any());
  }

  @Test
  @DisplayName("Should delete a shift through the lifecycle route")
  void delete_returnsNoContent() throws Exception {
    mockMvc
        .perform(
            delete(
                    "/api/v1/institutions/{institutionId}/shifts/{shiftId}",
                    INSTITUTION_ID,
                    SHIFT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Baja solicitada\"}"))
        .andExpect(status().isNoContent());

    verify(lifecycleService).deleteShift(eq(INSTITUTION_ID), eq(SHIFT_ID), any());
  }

  @Test
  @DisplayName("Should restore a deleted shift through the lifecycle route")
  void restore_returnsNoContent() throws Exception {
    mockMvc
        .perform(
            post(
                "/api/v1/institutions/{institutionId}/shifts/{shiftId}/restore",
                INSTITUTION_ID,
                SHIFT_ID))
        .andExpect(status().isNoContent());

    verify(lifecycleService).restoreShift(eq(INSTITUTION_ID), eq(SHIFT_ID), isNull());
  }

  @Test
  @DisplayName("Should create a shift under the platform administration route")
  void create_viaAdminRoute() throws Exception {
    when(createShiftUseCase.execute(eq(INSTITUTION_ID), any()))
        .thenReturn(
            new ShiftResponse(
                SHIFT_ID,
                INSTITUTION_ID,
                "Institución de prueba",
                "Turno mañana",
                null,
                true,
                null));

    mockMvc
        .perform(
            post("/api/v1/admin/institutions/{institutionId}/shifts", INSTITUTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno mañana\",\"description\":null}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(SHIFT_ID.toString()));
  }

  @Test
  @DisplayName("Should delete a shift under the platform administration route")
  void delete_viaAdminRoute() throws Exception {
    mockMvc
        .perform(
            delete(
                "/api/v1/admin/institutions/{institutionId}/shifts/{shiftId}",
                INSTITUTION_ID,
                SHIFT_ID))
        .andExpect(status().isNoContent());

    verify(lifecycleService).deleteShift(eq(INSTITUTION_ID), eq(SHIFT_ID), isNull());
  }

  private static PaginatedResponse<ShiftResponse> emptyPage() {
    return PaginatedResponse.<ShiftResponse>builder()
        .items(List.of())
        .page(0)
        .size(20)
        .totalItems(0)
        .totalPages(0)
        .build();
  }
}
