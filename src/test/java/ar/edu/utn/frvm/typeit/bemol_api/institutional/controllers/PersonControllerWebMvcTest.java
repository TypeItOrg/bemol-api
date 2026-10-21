package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.AddressResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CitySummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetPersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonUseCase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(PersonController.class)
@Import({PermissionAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PersonControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID CITY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private GetPersonUseCase getPersonUseCase;
  @MockitoBean private UpdatePersonUseCase updatePersonUseCase;

  @Test
  @DisplayName("Should return person profile for an authenticated user")
  void me_returnsPersonProfile() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    when(getPersonUseCase.execute(any()))
        .thenReturn(
            PersonResponse.builder()
                .personId(PERSON_ID)
                .firstName("Juan")
                .lastName("Pérez")
                .documentNumber("12345678")
                .institutionId(INSTITUTION_ID)
                .institutionName("Conservatorio Boero")
                .build());

    mockMvc
        .perform(get("/api/v1/person/me").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Juan"))
        .andExpect(jsonPath("$.lastName").value("Pérez"))
        .andExpect(jsonPath("$.documentNumber").value("12345678"))
        .andExpect(jsonPath("$.institutionName").value("Conservatorio Boero"));
  }

  @Test
  @DisplayName("Should update person profile for an authenticated user")
  void updateMe_returnsUpdatedProfile() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    when(updatePersonUseCase.execute(any(), any(UpdatePersonRequest.class)))
        .thenReturn(
            PersonResponse.builder()
                .personId(PERSON_ID)
                .firstName("Carlos")
                .lastName("Pérez")
                .documentNumber("12345678")
                .institutionId(INSTITUTION_ID)
                .institutionName("Conservatorio Boero")
                .phoneNumber("0353-123456")
                .address(
                    AddressResponse.builder()
                        .id(UUID.randomUUID())
                        .street("San Martín")
                        .number("123")
                        .city(
                            CitySummaryResponse.builder()
                                .id(CITY_ID)
                                .name("Villa María")
                                .provinceId(UUID.randomUUID())
                                .province("Córdoba")
                                .build())
                        .build())
                .build());

    mockMvc
        .perform(
            put("/api/v1/person/me")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "firstName": "Carlos",
                      "phoneNumber": "0353-123456",
                      "currentPassword": "old-password",
                      "password": "new-password",
                      "address": {
                        "cityId": "%s",
                        "street": "San Martín",
                        "number": "123"
                      }
                    }
                    """
                        .formatted(CITY_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Carlos"))
        .andExpect(jsonPath("$.phoneNumber").value("0353-123456"))
        .andExpect(jsonPath("$.address.street").value("San Martín"));
  }

  @Test
  @DisplayName("Should return bad request when email is invalid")
  void updateMe_returnsBadRequestForInvalidEmail() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");

    mockMvc
        .perform(
            put("/api/v1/person/me")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\"}"))
        .andExpect(status().isBadRequest());
  }
}
