package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.CitySummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.CountryLocationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.ProvinceSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.UpdateInstitutionalInstitutionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetInstitutionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ResolveInstitutionPublicAccessUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionalInstitutionUseCase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(InstitutionalInstitutionController.class)
@Import({
  PermissionAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  InstitutionalCallerGuard.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class InstitutionalInstitutionControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_INSTITUTION_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID CITY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID PROVINCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID COUNTRY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

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
  @MockitoBean private GetInstitutionUseCase getInstitutionUseCase;
  @MockitoBean private UpdateInstitutionalInstitutionUseCase updateInstitutionalInstitutionUseCase;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should update institution details for authorized institutional user")
  void update_updatesInstitutionDetailsForAuthorizedUser() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_UPDATE)))
        .thenReturn(true);
    when(updateInstitutionalInstitutionUseCase.execute(
            eq(INSTITUTION_ID), any(UpdateInstitutionalInstitutionRequest.class)))
        .thenReturn(detailResponse());

    mockMvc
        .perform(
            put("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(updatePayload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(INSTITUTION_ID.toString()));
  }

  @Test
  @DisplayName("Should forbid update when permission is missing")
  void update_returnsForbiddenWhenPermissionMissing() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_UPDATE)))
        .thenReturn(false);

    mockMvc
        .perform(
            put("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(updatePayload()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should forbid update when calling another institution endpoint")
  void update_returnsForbiddenWhenAccessingOtherInstitution() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), OTHER_INSTITUTION_ID), "");

    mockMvc
        .perform(
            put("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(updatePayload()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should return bad request when request validation fails")
  void update_returnsBadRequestWhenNameIsBlank() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_UPDATE)))
        .thenReturn(true);

    mockMvc
        .perform(
            put("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(invalidPayload()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should return bad request when city does not exist on update")
  void update_returnsBadRequestWhenCityNotFound() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPermission(any(), eq(PermissionCode.INSTITUTION_UPDATE)))
        .thenReturn(true);
    when(updateInstitutionalInstitutionUseCase.execute(
            eq(INSTITUTION_ID), any(UpdateInstitutionalInstitutionRequest.class)))
        .thenThrow(new CityNotFoundException());

    mockMvc
        .perform(
            put("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(updatePayload()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La ciudad especificada no existe."));
  }

  private static InstitutionDetailResponse detailResponse() {
    return InstitutionDetailResponse.builder()
        .id(INSTITUTION_ID)
        .name("Conservatorio Boero")
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

  private static String updatePayload() {
    return """
        {
          "name": "Conservatorio Boero",
          "cityId": "%s",
          "street": "San Martin",
          "number": "123",
          "neighborhood": "Centro",
          "additionalInfo": "Piso 1",
          "phoneNumber": "0353-123456",
          "email": "info@boero.edu.ar"
        }
        """
        .formatted(CITY_ID);
  }

  private static String invalidPayload() {
    return """
        {
          "name": "",
          "cityId": "%s"
        }
        """
        .formatted(CITY_ID);
  }
}
