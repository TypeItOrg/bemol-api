package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CitySummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CountryLocationResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionAdminListItemResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionListItemResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.ProvinceSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.CreateInstitutionRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionWithBrandingRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.CreateInstitutionUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GetInstitutionAdminUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GetInstitutionUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.InstitutionLogoUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListInstitutionsAdminUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListInstitutionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ResolveInstitutionPublicAccessUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UpdateInstitutionStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UpdateInstitutionWithBrandingUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({InstitutionController.class, PlatformInstitutionController.class})
@Import({RoleAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class InstitutionControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CITY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PROVINCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID COUNTRY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InstitutionLogoUseCase logos;
  @MockitoBean private ResolveInstitutionPublicAccessUseCase resolvePublicAccess;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListInstitutionsUseCase listInstitutionsUseCase;
  @MockitoBean private ListInstitutionsAdminUseCase listInstitutionsAdminUseCase;
  @MockitoBean private GetInstitutionUseCase getInstitutionUseCase;
  @MockitoBean private GetInstitutionAdminUseCase getInstitutionAdminUseCase;
  @MockitoBean private CreateInstitutionUseCase createInstitutionUseCase;
  @MockitoBean private UpdateInstitutionWithBrandingUseCase updateInstitutionUseCase;
  @MockitoBean private UpdateInstitutionStatusUseCase updateInstitutionStatusUseCase;

  @Test
  @DisplayName("Should list institutions without authentication")
  void list_returnsPublicInstitutions() throws Exception {
    when(listInstitutionsUseCase.execute(isNull(), isNull(), any()))
        .thenReturn(
            PaginatedResponse.<InstitutionListItemResponse>builder()
                .items(
                    List.of(
                        InstitutionListItemResponse.builder()
                            .id(INSTITUTION_ID)
                            .name("Conservatorio Boero")
                            .slug("boero-villa-maria")
                            .country(
                                CountryLocationResponse.builder()
                                    .countryId(COUNTRY_ID)
                                    .name("Argentina")
                                    .isoCode("ARG")
                                    .build())
                            .city("Villa Maria")
                            .province("Cordoba")
                            .active(true)
                            .build()))
                .page(0)
                .size(20)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(get("/api/v1/institutions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(INSTITUTION_ID.toString()))
        .andExpect(jsonPath("$.items[0].slug").value("boero-villa-maria"))
        .andExpect(jsonPath("$.items[0].country.countryId").value(COUNTRY_ID.toString()))
        .andExpect(jsonPath("$.items[0].country.name").value("Argentina"))
        .andExpect(jsonPath("$.items[0].country.isoCode").value("ARG"))
        .andExpect(jsonPath("$.items[0].active").value(true))
        .andExpect(jsonPath("$.items[0].email").doesNotExist())
        .andExpect(jsonPath("$.items[0].phoneNumber").doesNotExist());
  }

  @Test
  @DisplayName("Should pass public institution filters to use case")
  void list_passesPublicFiltersToUseCase() throws Exception {
    when(listInstitutionsUseCase.execute(eq("boero"), eq(true), any()))
        .thenReturn(
            PaginatedResponse.<InstitutionListItemResponse>builder()
                .items(List.of())
                .page(0)
                .size(20)
                .totalItems(0)
                .totalPages(0)
                .build());

    mockMvc
        .perform(
            get("/api/v1/institutions").queryParam("search", "boero").queryParam("active", "true"))
        .andExpect(status().isOk());

    verify(listInstitutionsUseCase).execute(eq("boero"), eq(true), any());
  }

  @Test
  @DisplayName("Should return institution detail without authentication")
  void get_returnsPublicInstitutionDetail() throws Exception {
    when(getInstitutionUseCase.execute(INSTITUTION_ID)).thenReturn(detailResponse());

    mockMvc
        .perform(get("/api/v1/institutions/{id}", INSTITUTION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(INSTITUTION_ID.toString()))
        .andExpect(jsonPath("$.slug").value("boero-villa-maria"))
        .andExpect(jsonPath("$.city.cityId").value(CITY_ID.toString()))
        .andExpect(jsonPath("$.city.name").value("Villa Maria"))
        .andExpect(jsonPath("$.province.provinceId").value(PROVINCE_ID.toString()))
        .andExpect(jsonPath("$.province.name").value("Cordoba"))
        .andExpect(jsonPath("$.country.countryId").value(COUNTRY_ID.toString()))
        .andExpect(jsonPath("$.country.name").value("Argentina"))
        .andExpect(jsonPath("$.country.isoCode").value("ARG"));
  }

  @Test
  @DisplayName("Should forbid unauthenticated institution creation")
  void create_returnsForbiddenWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/institutions")
                .contentType(APPLICATION_JSON)
                .content(createPayload()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should forbid institutional principals from creating institutions")
  void create_returnsForbiddenForInstitutionalPrincipal() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    stubPlatformAdminAccess(false);

    mockMvc
        .perform(
            post("/api/v1/admin/institutions")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createPayload()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should create institution for platform admin")
  void create_returnsCreatedForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(createInstitutionUseCase.execute(any(CreateInstitutionRequest.class)))
        .thenReturn(detailResponse());

    mockMvc
        .perform(
            post("/api/v1/admin/institutions")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createPayload()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("boero-villa-maria"));
  }

  @Test
  @DisplayName("Should forbid institutional principals from updating institutions")
  void update_returnsForbiddenForInstitutionalPrincipal() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    stubPlatformAdminAccess(false);

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/admin/institutions/{id}", INSTITUTION_ID)
                .file(updateData(updatePayload()))
                .principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should update institution for platform admin")
  void update_returnsOkForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(updateInstitutionUseCase.execute(
            eq(INSTITUTION_ID), any(UpdateInstitutionWithBrandingRequest.class), isNull()))
        .thenReturn(detailResponse());

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/admin/institutions/{id}", INSTITUTION_ID)
                .file(updateData(updatePayload()))
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(INSTITUTION_ID.toString()));
  }

  @Test
  @DisplayName("Should return bad request when city does not exist on create")
  void create_returnsBadRequestWhenCityNotFound() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(createInstitutionUseCase.execute(any(CreateInstitutionRequest.class)))
        .thenThrow(new CityNotFoundException());

    mockMvc
        .perform(
            post("/api/v1/admin/institutions")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createPayload()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La ciudad especificada no existe."));
  }

  @Test
  @DisplayName("Should return bad request when cityId is malformed on create")
  void create_returnsBadRequestWhenCityIdIsInvalid() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);

    mockMvc
        .perform(
            post("/api/v1/admin/institutions")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(invalidCityIdPayload()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should return bad request when city does not exist on update")
  void update_returnsBadRequestWhenCityNotFound() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(updateInstitutionUseCase.execute(
            eq(INSTITUTION_ID), any(UpdateInstitutionWithBrandingRequest.class), isNull()))
        .thenThrow(new CityNotFoundException());

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/admin/institutions/{id}", INSTITUTION_ID)
                .file(updateData(updatePayload()))
                .principal(authentication))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La ciudad especificada no existe."));
  }

  @Test
  @DisplayName("Should return bad request when cityId is malformed on update")
  void update_returnsBadRequestWhenCityIdIsInvalid() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/admin/institutions/{id}", INSTITUTION_ID)
                .file(updateData(invalidCityIdPayload()))
                .principal(authentication))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should forbid unauthenticated admin institution listing")
  void adminList_returnsForbiddenWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/admin/institutions")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should forbid institutional principals from listing admin institutions")
  void adminList_returnsForbiddenForInstitutionalPrincipal() throws Exception {
    var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    stubPlatformAdminAccess(false);

    mockMvc
        .perform(get("/api/v1/admin/institutions").principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should return admin institution list with userCount for platform admin")
  void adminList_returnsAdminListForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(listInstitutionsAdminUseCase.execute(isNull(), isNull(), any()))
        .thenReturn(
            PaginatedResponse.<InstitutionAdminListItemResponse>builder()
                .items(
                    List.of(
                        InstitutionAdminListItemResponse.builder()
                            .id(INSTITUTION_ID)
                            .name("Conservatorio Boero")
                            .slug("boero-villa-maria")
                            .country(
                                CountryLocationResponse.builder()
                                    .countryId(COUNTRY_ID)
                                    .name("Argentina")
                                    .isoCode("ARG")
                                    .build())
                            .city("Villa Maria")
                            .province("Cordoba")
                            .active(true)
                            .userCount(7)
                            .build()))
                .page(0)
                .size(20)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(get("/api/v1/admin/institutions").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(INSTITUTION_ID.toString()))
        .andExpect(jsonPath("$.items[0].userCount").value(7))
        .andExpect(jsonPath("$.items[0].email").doesNotExist())
        .andExpect(jsonPath("$.items[0].phoneNumber").doesNotExist());
  }

  @Test
  @DisplayName("Should pass admin institution filters to use case")
  void adminList_passesFiltersToUseCase() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    stubPlatformAdminAccess(true);
    when(listInstitutionsAdminUseCase.execute(eq("boero"), eq(true), any()))
        .thenReturn(
            PaginatedResponse.<InstitutionAdminListItemResponse>builder()
                .items(List.of())
                .page(0)
                .size(20)
                .totalItems(0)
                .totalPages(0)
                .build());

    mockMvc
        .perform(
            get("/api/v1/admin/institutions")
                .principal(authentication)
                .param("search", "boero")
                .param("active", "true")
                .param("sort", "active,desc"))
        .andExpect(status().isOk());

    verify(listInstitutionsAdminUseCase).execute(eq("boero"), eq(true), any());
  }

  private void stubPlatformAdminAccess(boolean allowed) {
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(allowed);
  }

  private static InstitutionDetailResponse detailResponse() {
    return InstitutionDetailResponse.builder()
        .id(INSTITUTION_ID)
        .name("Conservatorio Boero")
        .slug("boero-villa-maria")
        .city(CitySummaryResponse.builder().cityId(CITY_ID).name("Villa Maria").build())
        .province(ProvinceSummaryResponse.builder().provinceId(PROVINCE_ID).name("Cordoba").build())
        .country(
            CountryLocationResponse.builder()
                .countryId(COUNTRY_ID)
                .name("Argentina")
                .isoCode("ARG")
                .build())
        .street("San Martin")
        .number("123")
        .neighborhood("Centro")
        .phoneNumber("0353-123456")
        .email("info@boero.edu.ar")
        .active(true)
        .build();
  }

  private static String createPayload() {
    return """
        {
          "name": "Conservatorio Boero",
          "slug": "boero-villa-maria",
          "cityId": "%s"
        }
        """
        .formatted(CITY_ID);
  }

  private static MockMultipartFile updateData(final String institution) {
    final String payload =
        """
        {"institution": %s, "publicSubdomain": null, "logoIntent": "KEEP"}
        """
            .formatted(institution);
    return new MockMultipartFile(
        "data",
        "data.json",
        "application/json",
        payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String updatePayload() {
    return """
        {
          "name": "Conservatorio Boero",
          "slug": "boero-villa-maria",
          "cityId": "%s",
          "active": true
        }
        """
        .formatted(CITY_ID);
  }

  private static String invalidCityIdPayload() {
    return """
        {
          "name": "Conservatorio Boero",
          "slug": "boero-villa-maria",
          "cityId": "not-a-uuid"
        }
        """;
  }
}
