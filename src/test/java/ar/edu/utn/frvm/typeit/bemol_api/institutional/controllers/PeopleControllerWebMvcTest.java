package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PersonNotFoundInInstitutionException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.CreatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.DeletePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetPersonByIdUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListPeopleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionalUserStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonByAdminUseCase;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({PeopleController.class, AdminPeopleController.class})
@Import({
  RoleAuthorizationAspect.class,
  PermissionAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  InstitutionalCallerGuard.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PeopleControllerWebMvcTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_INSTITUTION_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;

  @MockitoBean PersonOwnAccessService ownAccess;
  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private ListPeopleUseCase listPeopleUseCase;
  @MockitoBean private GetPersonByIdUseCase getPersonByIdUseCase;
  @MockitoBean private CreatePersonUseCase createPersonUseCase;
  @MockitoBean private UpdatePersonByAdminUseCase updatePersonByAdminUseCase;
  @MockitoBean private DeletePersonUseCase deletePersonUseCase;
  @MockitoBean private UpdateInstitutionalUserStatusUseCase updateInstitutionalUserStatusUseCase;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should list people for platform admin")
  void listPeople_returnsOkForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(listPeopleUseCase.execute(eq(INSTITUTION_ID), eq("ana"), eq(null), any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.from(
                new PageImpl<>(List.of(personSummary()), Pageable.ofSize(20), 1)));

    mockMvc
        .perform(
            get("/api/v1/admin/institutions/{institutionId}/people", INSTITUTION_ID)
                .param("search", "ana")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].documentNumber").value("12345678"));
  }

  @Test
  @DisplayName("Should list people for authority of same institution")
  void listPeople_returnsOkForSameInstitutionAuthority() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_READ_ANY, true);
    when(listPeopleUseCase.execute(eq(INSTITUTION_ID), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(PaginatedResponse.from(new PageImpl<>(List.of(), Pageable.ofSize(20), 0)));

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should list people filtered by role id")
  void listPeople_filtersByRoleId() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_READ_ANY, true);

    UUID filterRoleId = UUID.randomUUID();
    when(listPeopleUseCase.execute(
            eq(INSTITUTION_ID), eq(null), eq(filterRoleId), any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.from(
                new PageImpl<>(List.of(personSummary()), Pageable.ofSize(20), 1)));

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/people", INSTITUTION_ID)
                .param("roleId", filterRoleId.toString())
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].documentNumber").value("12345678"));
  }

  @Test
  @DisplayName("Should forbid authority of different institution")
  void listPeople_returnsForbiddenForCrossTenant() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_READ_ANY, true);

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/people", OTHER_INSTITUTION_ID)
                .principal(authentication))
        .andExpect(status().isForbidden());

    verify(listPeopleUseCase, never()).execute(any(), any(), any(), any(Pageable.class));
  }

  @Test
  @DisplayName("Should forbid applicant without read permission")
  void listPeople_returnsForbiddenWithoutPermission() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_READ_ANY, false);

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should get person for platform admin")
  void getPerson_returnsOkForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(getPersonByIdUseCase.execute(INSTITUTION_ID, PERSON_ID)).thenReturn(personResponse());

    mockMvc
        .perform(
            get(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personId").value(PERSON_ID.toString()));
  }

  @Test
  @DisplayName("Should get person for authority of own institution")
  void getPerson_returnsOkForSameInstitutionAuthority() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_READ_ANY, true);
    when(getPersonByIdUseCase.execute(INSTITUTION_ID, PERSON_ID)).thenReturn(personResponse());

    mockMvc
        .perform(
            get("/api/v1/institutions/{institutionId}/people/{personId}", INSTITUTION_ID, PERSON_ID)
                .principal(authentication))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should create person for platform admin")
  void createPerson_returnsCreatedForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(createPersonUseCase.execute(eq(INSTITUTION_ID), any())).thenReturn(personResponse());

    mockMvc
        .perform(
            post("/api/v1/admin/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.personId").value(PERSON_ID.toString()));
  }

  @Test
  @DisplayName("Should create person for authority of own institution")
  void createPerson_returnsCreatedForSameInstitutionAuthority() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_CREATE, true);
    when(createPersonUseCase.execute(eq(INSTITUTION_ID), any())).thenReturn(personResponse());

    mockMvc
        .perform(
            post("/api/v1/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Should reject people younger than three")
  void createPerson_returnsBadRequestForTooRecentBirthDate() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    LocalDate invalidBirthDate =
        LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusYears(3).plusDays(1);

    mockMvc
        .perform(
            post("/api/v1/admin/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createBody(invalidBirthDate)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.fieldErrors.birthDate").value("La persona debe tener al menos 3 años."));

    verify(createPersonUseCase, never()).execute(eq(INSTITUTION_ID), any());
  }

  @Test
  @DisplayName("Should return 409 when create with duplicate document")
  void createPerson_returns409ForDuplicate() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(createPersonUseCase.execute(eq(INSTITUTION_ID), any()))
        .thenThrow(new PersonAlreadyExistsException());

    mockMvc
        .perform(
            post("/api/v1/admin/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Should return 404 when create with non-existent institution")
  void createPerson_returns404ForMissingInstitution() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(createPersonUseCase.execute(eq(INSTITUTION_ID), any()))
        .thenThrow(new InstitutionNotFoundException());

    mockMvc
        .perform(
            post("/api/v1/admin/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Should update person for platform admin")
  void updatePerson_returnsOkForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(updatePersonByAdminUseCase.execute(eq(INSTITUTION_ID), eq(PERSON_ID), any()))
        .thenReturn(personResponse());

    mockMvc
        .perform(
            put(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"firstName\":\"Ana María\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should return 400 when update with all-null fields")
  void updatePerson_returns400ForAllNullFields() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(updatePersonByAdminUseCase.execute(eq(INSTITUTION_ID), eq(PERSON_ID), any()))
        .thenThrow(new ConstraintViolationException(java.util.Set.of()));

    mockMvc
        .perform(
            put(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should return 404 when updating person from another institution")
  void updatePerson_returns404ForCrossTenant() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    when(updatePersonByAdminUseCase.execute(eq(INSTITUTION_ID), eq(PERSON_ID), any()))
        .thenThrow(new PersonNotFoundInInstitutionException());

    mockMvc
        .perform(
            put(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"firstName\":\"Ana\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Should delete person for platform admin")
  void deletePerson_returnsNoContentForPlatformAdmin() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);

    mockMvc
        .perform(
            delete(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(deletePersonUseCase).execute(INSTITUTION_ID, PERSON_ID);
  }

  @Test
  @DisplayName("Should return 404 when deleting person from another institution")
  void deletePerson_returns404ForCrossTenant() throws Exception {
    var authentication = new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPlatformAdminAccess(true);
    org.mockito.Mockito.doThrow(new PersonNotFoundInInstitutionException())
        .when(deletePersonUseCase)
        .execute(INSTITUTION_ID, PERSON_ID);

    mockMvc
        .perform(
            delete(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}",
                    INSTITUTION_ID,
                    PERSON_ID)
                .principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Should forbid applicant from create without permission")
  void createPerson_returnsForbiddenWithoutPermission() throws Exception {
    var authentication =
        new TestingAuthenticationToken(institutionalPrincipal(USER_ID, INSTITUTION_ID), "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubPermission(PermissionCode.INSTITUTION_PERSON_CREATE, false);

    mockMvc
        .perform(
            post("/api/v1/institutions/{institutionId}/people", INSTITUTION_ID)
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isForbidden());
  }

  private void stubPermission(PermissionCode permission, boolean allowed) {
    when(authorizationService.hasPermission(any(), eq(permission))).thenReturn(allowed);
  }

  private void stubPlatformAdminAccess(boolean allowed) {
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(allowed);
  }

  private static PersonSummaryResponse personSummary() {
    return new PersonSummaryResponse(
        PERSON_ID,
        "Ana",
        "García",
        "12345678",
        "ana@example.com",
        null,
        true,
        List.of(),
        true,
        false);
  }

  private static PersonResponse personResponse() {
    Institution institution = Institution.builder().id(INSTITUTION_ID).name("Boero").build();
    Person person =
        Person.builder()
            .id(PERSON_ID)
            .institution(institution)
            .firstName("Ana")
            .lastName("García")
            .documentNumber("12345678")
            .build();
    return PersonResponse.from(person);
  }

  private static String createBody() {
    return "{"
        + "\"firstName\":\"Ana\","
        + "\"lastName\":\"García\","
        + "\"documentNumber\":\"12345678\","
        + "\"email\":\"ana@example.com\","
        + "\"birthDate\":\"2010-01-01\","
        + "\"password\":\"admin-pass-123\""
        + "}";
  }

  private static String createBody(LocalDate birthDate) {
    return "{"
        + "\"firstName\":\"Ana\","
        + "\"lastName\":\"García\","
        + "\"documentNumber\":\"12345678\","
        + "\"email\":\"ana@example.com\","
        + "\"birthDate\":\""
        + birthDate
        + "\","
        + "\"password\":\"admin-pass-123\""
        + "}";
  }
}
