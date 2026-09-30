package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UnlinkGuardianDependentUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(GuardianDependentController.class)
@Import({
  RoleAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class GuardianDependentControllerWebMvcTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID TUTOR_ID = UUID.randomUUID();
  private static final UUID DEPENDENT_ID = UUID.randomUUID();
  private static final String BASE_PATH =
      "/api/v1/institutions/{institutionId}/guardian/dependents";

  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListGuardianDependentsUseCase listGuardianDependentsUseCase;
  @MockitoBean private RegisterGuardianDependentUseCase registerGuardianDependentUseCase;
  @MockitoBean private UnlinkGuardianDependentUseCase unlinkGuardianDependentUseCase;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should list the dependents of the authenticated tutor")
  void list_returnsDependents() throws Exception {
    final var auth = authentication();
    allowManagingDependents();
    when(listGuardianDependentsUseCase.execute(INSTITUTION_ID, TUTOR_ID))
        .thenReturn(List.of(response()));

    mockMvc
        .perform(get(BASE_PATH, INSTITUTION_ID).principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].dependentPersonId").value(DEPENDENT_ID.toString()))
        .andExpect(jsonPath("$[0].relationship").value("FATHER"))
        .andExpect(jsonPath("$[0].isPrimaryContact").value(true))
        .andExpect(jsonPath("$[0].activeApplicationsCount").value(1))
        .andExpect(jsonPath("$[0].roles[0]").value("Postulante"));
  }

  @Test
  @DisplayName("Should forbid managing dependents without the permission")
  void list_isForbiddenWithoutPermission() throws Exception {
    mockMvc
        .perform(get(BASE_PATH, INSTITUTION_ID).principal(authentication()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should register a dependent and return 201")
  void create_returnsCreated() throws Exception {
    final var auth = authentication();
    allowManagingDependents();
    when(registerGuardianDependentUseCase.execute(
            eq(INSTITUTION_ID), eq(TUTOR_ID), any(CreateGuardianDependentRequest.class)))
        .thenReturn(response());

    mockMvc
        .perform(
            post(BASE_PATH, INSTITUTION_ID)
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dependentPersonId").value(DEPENDENT_ID.toString()));
  }

  @Test
  @DisplayName("Should reject an invalid document number")
  void create_rejectsInvalidDocument() throws Exception {
    final var auth = authentication();
    allowManagingDependents();
    final Map<String, Object> body = new java.util.HashMap<>(validBody());
    body.put("documentNumber", "123");

    mockMvc
        .perform(
            post(BASE_PATH, INSTITUTION_ID)
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject a birth date in the future")
  void create_rejectsFutureBirthDate() throws Exception {
    final var auth = authentication();
    allowManagingDependents();
    final Map<String, Object> body = new java.util.HashMap<>(validBody());
    body.put("birthDate", LocalDate.now().plusDays(1).toString());

    mockMvc
        .perform(
            post(BASE_PATH, INSTITUTION_ID)
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should answer 409 with the error code when the dependent is already linked")
  void create_returnsConflictWhenAlreadyLinked() throws Exception {
    final var auth = authentication();
    allowManagingDependents();
    when(registerGuardianDependentUseCase.execute(
            eq(INSTITUTION_ID), eq(TUTOR_ID), any(CreateGuardianDependentRequest.class)))
        .thenThrow(new DependentAlreadyLinkedException());

    mockMvc
        .perform(
            post(BASE_PATH, INSTITUTION_ID)
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEPENDENT_ALREADY_LINKED"));
  }

  @Test
  @DisplayName("Should unlink a dependent and return 204")
  void delete_returnsNoContent() throws Exception {
    final var auth = authentication();
    allowManagingDependents();

    mockMvc
        .perform(
            delete(BASE_PATH + "/{dependentPersonId}", INSTITUTION_ID, DEPENDENT_ID)
                .principal(auth))
        .andExpect(status().isNoContent());

    verify(unlinkGuardianDependentUseCase).execute(INSTITUTION_ID, TUTOR_ID, DEPENDENT_ID);
  }

  private void allowManagingDependents() {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)))
        .thenReturn(true);
  }

  private Map<String, Object> validBody() {
    return Map.of(
        "documentNumber", "54123456",
        "firstName", "Mateo",
        "lastName", "Gonzalez",
        "birthDate", "2018-09-10",
        "relationship", "FATHER",
        "isPrimaryContact", true);
  }

  private GuardianDependentResponse response() {
    return new GuardianDependentResponse(
        UUID.randomUUID(),
        DEPENDENT_ID,
        "54123456",
        "Mateo",
        "Gonzalez",
        LocalDate.of(2018, 9, 10),
        GuardianRelationship.FATHER,
        true,
        1,
        List.of("Postulante"),
        Instant.parse("2026-09-24T12:00:00Z"));
  }

  private TestingAuthenticationToken authentication() {
    final var user =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(TUTOR_ID)
            .documentNumber("35123456")
            .institutionId(INSTITUTION_ID)
            .sessionId(UUID.randomUUID())
            .tokenId("token-id")
            .build();
    final var auth = new TestingAuthenticationToken(user, null);
    auth.setAuthenticated(true);
    return auth;
  }
}
