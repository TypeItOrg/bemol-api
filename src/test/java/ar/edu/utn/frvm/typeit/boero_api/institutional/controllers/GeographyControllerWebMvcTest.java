package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CountryNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.ProvinceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CityListItemResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CountrySummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.ProvinceListItemResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListCitiesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListCountriesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListProvincesUseCase;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({CityController.class, CountryController.class, ProvinceController.class})
@Import({GlobalExceptionHandler.class, WebConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class GeographyControllerWebMvcTest {
  private static final UUID COUNTRY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PROVINCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CITY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final PageRequest DEFAULT_PAGE = PageRequest.of(0, 10, Sort.by("name"));
  private static final PageRequest SEARCH_PAGE = PageRequest.of(2, 20, Sort.by("name"));

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListCitiesUseCase cities;
  @MockitoBean private ListCountriesUseCase countries;
  @MockitoBean private ListProvincesUseCase provinces;

  static Stream<Arguments> catalogs() {
    return Stream.of(
        Arguments.of("/cities", "city"),
        Arguments.of("/countries", "country"),
        Arguments.of("/provinces", "province"),
        Arguments.of("/countries/" + COUNTRY_ID + "/provinces", "country-provinces"),
        Arguments.of("/provinces/" + PROVINCE_ID + "/cities", "province-cities"));
  }

  @ParameterizedTest
  @MethodSource("catalogs")
  void preservesTheEntireResponseAndBindsOptionalSearchAndPagination(String path, String kind)
      throws Exception {
    String expectedItem = stubCatalog(kind);

    mockMvc
        .perform(
            get("/api/v1" + path)
                .param("search", "consulta")
                .param("page", "2")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(
            content().json(expectedPage(expectedItem, 2, 20, 41, 3), JsonCompareMode.STRICT));

    mockMvc
        .perform(get("/api/v1" + path))
        .andExpect(status().isOk())
        .andExpect(content().json(expectedPage(expectedItem, 0, 10, 1, 1), JsonCompareMode.STRICT));
  }

  private String stubCatalog(String kind) {
    var city = new CityListItemResponse(CITY_ID, "Villa María", PROVINCE_ID, "Córdoba");
    var province = new ProvinceListItemResponse(PROVINCE_ID, "Córdoba");
    var country = new CountrySummaryResponse(COUNTRY_ID, "Argentina", "ARG");
    switch (kind) {
      case "city" -> {
        when(cities.execute("consulta", SEARCH_PAGE)).thenReturn(page(city, 2, 20, 41, 3));
        when(cities.execute(null, DEFAULT_PAGE)).thenReturn(page(city, 0, 10, 1, 1));
      }
      case "province-cities" -> {
        when(cities.executeByProvince(PROVINCE_ID, "consulta", SEARCH_PAGE))
            .thenReturn(page(city, 2, 20, 41, 3));
        when(cities.executeByProvince(PROVINCE_ID, null, DEFAULT_PAGE))
            .thenReturn(page(city, 0, 10, 1, 1));
      }
      case "country" -> {
        when(countries.execute("consulta", SEARCH_PAGE)).thenReturn(page(country, 2, 20, 41, 3));
        when(countries.execute(null, DEFAULT_PAGE)).thenReturn(page(country, 0, 10, 1, 1));
      }
      case "province" -> {
        when(provinces.execute("consulta", SEARCH_PAGE)).thenReturn(page(province, 2, 20, 41, 3));
        when(provinces.execute(null, DEFAULT_PAGE)).thenReturn(page(province, 0, 10, 1, 1));
      }
      case "country-provinces" -> {
        when(provinces.executeByCountry(COUNTRY_ID, "consulta", SEARCH_PAGE))
            .thenReturn(page(province, 2, 20, 41, 3));
        when(provinces.executeByCountry(COUNTRY_ID, null, DEFAULT_PAGE))
            .thenReturn(page(province, 0, 10, 1, 1));
      }
      default -> throw new IllegalArgumentException(kind);
    }
    return switch (kind) {
      case "country" ->
          """
          {"id":"11111111-1111-1111-1111-111111111111","name":"Argentina","isoCode":"ARG"}
          """;
      case "province", "country-provinces" ->
          """
          {"id":"22222222-2222-2222-2222-222222222222","name":"Córdoba"}
          """;
      default ->
          """
          {"id":"33333333-3333-3333-3333-333333333333","name":"Villa María",
           "provinceId":"22222222-2222-2222-2222-222222222222","province":"Córdoba"}
          """;
    };
  }

  @Test
  void anEmptySearchResultKeepsThePaginationContract() throws Exception {
    when(cities.execute("sin resultados", DEFAULT_PAGE))
        .thenReturn(new PaginatedResponse<>(List.of(), 0, 10, 0, 0));
    mockMvc
        .perform(get("/api/v1/cities").param("search", "sin resultados"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
            {"items":[],"page":0,"size":10,"totalItems":0,"totalPages":0}
            """,
                    JsonCompareMode.STRICT));
  }

  @ParameterizedTest
  @ValueSource(strings = {"country", "province"})
  void missingParentsReturn404InsteadOfAnEmptyCatalog(String parent) throws Exception {
    UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
    String path;
    if (parent.equals("country")) {
      when(provinces.executeByCountry(eq(missing), eq(null), any()))
          .thenThrow(new CountryNotFoundException());
      path = "/countries/" + missing + "/provinces";
    } else {
      when(cities.executeByProvince(eq(missing), eq(null), any()))
          .thenThrow(new ProvinceNotFoundException());
      path = "/provinces/" + missing + "/cities";
    }
    mockMvc.perform(get("/api/v1" + path)).andExpect(status().isNotFound());
  }

  private static <T> PaginatedResponse<T> page(T item, int index, int size, long total, int pages) {
    return new PaginatedResponse<>(List.of(item), index, size, total, pages);
  }

  private static String expectedPage(String item, int index, int size, long total, int pages) {
    return """
        {"items":[%s],"page":%d,"size":%d,"totalItems":%d,"totalPages":%d}
        """
        .formatted(item, index, size, total, pages);
  }
}
