package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormAssignmentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormCatalogUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormVersionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ExtractEnrollmentPdfDataUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest({
  InstitutionalEnrollmentFormController.class,
  PlatformEnrollmentFormController.class,
  InstitutionalEnrollmentFormAssignmentController.class,
  PlatformEnrollmentFormAssignmentController.class
})
@Import({
  InstitutionAccessAspect.class,
  RoleAuthorizationAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class EnrollmentFormAdministrationWebMvcTest {
  private static final UUID INSTITUTION = UUID.randomUUID();
  private static final UUID FORM = UUID.randomUUID();
  private static final UUID VERSION = UUID.randomUUID();
  private static final UUID ASSIGNMENT = UUID.randomUUID();
  private static final UUID PATH = UUID.randomUUID();
  private static final UUID YEAR = UUID.randomUUID();
  private static final EnrollmentFormDefinition DEFINITION =
      new EnrollmentFormDefinition(List.of(), List.of());

  @Autowired private MockMvc mvc;
  @MockitoBean private EnrollmentFormCatalogUseCase catalog;
  @MockitoBean private EnrollmentFormVersionUseCase versions;
  @MockitoBean private EnrollmentFormAssignmentUseCase assignments;
  @MockitoBean private ExtractEnrollmentPdfDataUseCase pdf;
  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorization;
  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @MethodSource("mutations")
  void everyMutationRequiresUpdatePermission(final boolean platform, final Endpoint endpoint)
      throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_READ);

    mvc.perform(
            request(HttpMethod.valueOf(endpoint.method()), base(platform) + endpoint.suffix())
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(endpoint.body()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(catalog, versions, assignments, pdf);
  }

  @ParameterizedTest
  @MethodSource("reads")
  void everyReadRequiresReadPermission(final boolean platform, final String suffix)
      throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_UPDATE);

    mvc.perform(get(base(platform) + suffix).principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(catalog, versions, assignments);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listsModelsWithPagination(final boolean platform) throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_READ);
    when(catalog.list(eq(INSTITUTION), isNull(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(List.of(new EnrollmentFormResponse(FORM, INSTITUTION, "Talleres", 0))));

    mvc.perform(get(base(platform) + "/enrollment-forms").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(FORM.toString()))
        .andExpect(jsonPath("$.items[0].name").value("Talleres"))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void institutionMembershipIsCheckedBeforeReading() throws Exception {
    final var authentication = authenticate(false);
    permit(PermissionCode.INSTITUTION_READ);
    doThrow(new AccessDeniedException("Wrong institution"))
        .when(institutionalCallerGuard)
        .ensureCallerBelongsToInstitution(authentication, INSTITUTION);

    mvc.perform(get(base(false) + "/enrollment-forms").principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(catalog, versions, assignments);
  }

  @Test
  void institutionalPermissionDoesNotGrantThePlatformBoundary() throws Exception {
    final var authentication = authenticate(false);
    permit(PermissionCode.INSTITUTION_READ);
    permit(PermissionCode.INSTITUTION_UPDATE);

    mvc.perform(get(base(true) + "/enrollment-forms").principal(authentication))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(base(true) + "/enrollment-forms")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Talleres\"}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(catalog, versions, assignments);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void createsModelAndPublishesAnExplicitRevision(final boolean platform) throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_UPDATE);
    when(catalog.create(INSTITUTION, new EnrollmentFormCreateRequest("Talleres")))
        .thenReturn(new EnrollmentFormResponse(FORM, INSTITUTION, "Talleres", 0));
    when(versions.publish(INSTITUTION, FORM, VERSION, new EnrollmentFormRevisionRequest(2L)))
        .thenReturn(
            new EnrollmentFormVersionResponse(
                VERSION,
                FORM,
                INSTITUTION,
                1,
                EnrollmentFormVersionStatus.PUBLISHED,
                3,
                Instant.parse("2026-11-06T12:00:00Z"),
                null,
                DEFINITION));

    mvc.perform(
            post(base(platform) + "/enrollment-forms")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Talleres\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(FORM.toString()));
    mvc.perform(
            post(base(platform) + "/enrollment-forms/" + FORM + "/versions/" + VERSION + "/publish")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"expectedRevision\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.revision").value(3));

    verify(versions).publish(INSTITUTION, FORM, VERSION, new EnrollmentFormRevisionRequest(2L));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"expectedRevision\":-1}", "{\"expectedRevision\":null}"})
  void publicationRequiresAnExplicitNonnegativeRevision(final String body) throws Exception {
    final var authentication = authenticate(false);
    permit(PermissionCode.INSTITUTION_UPDATE);

    mvc.perform(
            post(base(false) + "/enrollment-forms/" + FORM + "/versions/" + VERSION + "/publish")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(versions);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void modelNamesCannotBeBlank(final String name) throws Exception {
    final var authentication = authenticate(false);
    permit(PermissionCode.INSTITUTION_UPDATE);

    mvc.perform(
            post(base(false) + "/enrollment-forms")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(catalog);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void previewsOnlyTheStructureWithoutRenderingPdf(final boolean platform) throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_UPDATE);
    when(catalog.preview(INSTITUTION, DEFINITION)).thenReturn(DEFINITION);

    mvc.perform(
            post(base(platform) + "/enrollment-forms/preview")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"sections\":[],\"pdfSections\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections").isArray());

    verify(catalog).preview(INSTITUTION, DEFINITION);
    verifyNoInteractions(versions, assignments);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void previewsPdfWithPrivateHeadersAndWithoutChangingModels(final boolean platform)
      throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_UPDATE);
    final var data =
        EnrollmentPdfData.builder()
            .institution(new EnrollmentPdfData.InstitutionDetails("Conservatorio", "", "", ""))
            .settings(EnrollmentPdfSettingsResponse.defaults())
            .generatedAt(Instant.parse("2026-11-06T12:00:00Z"))
            .sections(List.of())
            .coursesSelected(false)
            .selections(List.of())
            .build();
    when(pdf.formExample(INSTITUTION, DEFINITION)).thenReturn(data);

    mvc.perform(
            post(base(platform) + "/enrollment-forms/preview-pdf")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"sections\":[],\"pdfSections\":[]}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.institution.name").value("Conservatorio"));
    verifyNoInteractions(catalog, versions, assignments);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void createsAndRemovesAnAssignmentWithItsRevision(final boolean platform) throws Exception {
    final var authentication = authenticate(platform);
    permit(PermissionCode.INSTITUTION_UPDATE);
    when(assignments.create(
            INSTITUTION, new EnrollmentFormAssignmentCreateRequest(PATH, YEAR, VERSION)))
        .thenReturn(
            new EnrollmentFormAssignmentResponse(
                ASSIGNMENT,
                INSTITUTION,
                PATH,
                YEAR,
                FORM,
                VERSION,
                1,
                0,
                "Talleres",
                2026,
                "Ingreso"));

    mvc.perform(
            post(base(platform) + "/enrollment-form-assignments")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content(assignmentBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.formVersionId").value(VERSION.toString()));
    mvc.perform(
            delete(base(platform) + "/enrollment-form-assignments/" + ASSIGNMENT)
                .principal(authentication)
                .param("expectedRevision", "0"))
        .andExpect(status().isNoContent());

    verify(assignments).delete(INSTITUTION, ASSIGNMENT, 0);
  }

  @Test
  void assignmentDeletionRequiresRevision() throws Exception {
    final var authentication = authenticate(false);
    permit(PermissionCode.INSTITUTION_UPDATE);

    mvc.perform(
            delete(base(false) + "/enrollment-form-assignments/" + ASSIGNMENT)
                .principal(authentication))
        .andExpect(status().isBadRequest());
    mvc.perform(
            delete(base(false) + "/enrollment-form-assignments/" + ASSIGNMENT)
                .principal(authentication)
                .param("expectedRevision", "-1"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(assignments);
  }

  @Test
  void assignmentRequiresItsCompleteContext() throws Exception {
    final var authentication = authenticate(false);
    permit(PermissionCode.INSTITUTION_UPDATE);

    mvc.perform(
            post(base(false) + "/enrollment-form-assignments")
                .principal(authentication)
                .contentType(APPLICATION_JSON)
                .content("{\"formVersionId\":\"" + VERSION + "\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(assignments);
  }

  private Authentication authenticate(final boolean platform) {
    final var principal =
        platform
            ? AuthTestData.platformPrincipal(UUID.randomUUID())
            : JwtAuthenticatedUser.builder()
                .userId(UUID.randomUUID())
                .personId(UUID.randomUUID())
                .institutionId(INSTITUTION)
                .documentNumber("40100100")
                .sessionId(UUID.randomUUID())
                .tokenId("form-admin-test")
                .build();
    final var authentication = new TestingAuthenticationToken(principal, "");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    when(authorization.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN))
        .thenReturn(platform);
    return authentication;
  }

  private void permit(final PermissionCode permission) {
    when(authorization.hasPermission(any(Authentication.class), eq(permission))).thenReturn(true);
  }

  private static String base(final boolean platform) {
    return "/api/v1" + (platform ? "/admin" : "") + "/institutions/" + INSTITUTION;
  }

  private static String assignmentBody() {
    return "{\"trainingPathId\":\""
        + PATH
        + "\",\"academicYearId\":\""
        + YEAR
        + "\",\"formVersionId\":\""
        + VERSION
        + "\"}";
  }

  static Stream<Arguments> reads() {
    final var paths =
        List.of(
            "/enrollment-forms",
            "/enrollment-forms/base",
            "/enrollment-forms/" + FORM,
            "/enrollment-forms/" + FORM + "/versions",
            "/enrollment-forms/" + FORM + "/versions/" + VERSION,
            "/enrollment-form-assignments");
    return Stream.of(false, true)
        .flatMap(platform -> paths.stream().map(path -> Arguments.of(platform, path)));
  }

  static Stream<Arguments> mutations() {
    final var version = "/enrollment-forms/" + FORM + "/versions";
    final var endpoints =
        List.of(
            new Endpoint("POST", "/enrollment-forms", "{\"name\":\"Talleres\"}"),
            new Endpoint(
                "PUT",
                "/enrollment-forms/" + FORM,
                "{\"name\":\"Talleres\",\"expectedRevision\":0}"),
            new Endpoint(
                "POST", "/enrollment-forms/preview", "{\"sections\":[],\"pdfSections\":[]}"),
            new Endpoint(
                "POST", "/enrollment-forms/preview-pdf", "{\"sections\":[],\"pdfSections\":[]}"),
            new Endpoint("POST", version, "{\"sourceVersionId\":\"" + VERSION + "\"}"),
            new Endpoint(
                "PUT",
                version + "/" + VERSION,
                "{\"definition\":{\"sections\":[],\"pdfSections\":[]},\"expectedRevision\":0}"),
            new Endpoint("POST", version + "/" + VERSION + "/publish", "{\"expectedRevision\":0}"),
            new Endpoint("POST", version + "/" + VERSION + "/archive", "{\"expectedRevision\":0}"),
            new Endpoint("POST", "/enrollment-form-assignments", assignmentBody()),
            new Endpoint(
                "PUT",
                "/enrollment-form-assignments/" + ASSIGNMENT,
                "{\"formVersionId\":\"" + VERSION + "\",\"expectedRevision\":0}"),
            new Endpoint(
                "DELETE",
                "/enrollment-form-assignments/" + ASSIGNMENT + "?expectedRevision=0",
                ""));
    return Stream.of(false, true)
        .flatMap(platform -> endpoints.stream().map(endpoint -> Arguments.of(platform, endpoint)));
  }

  private record Endpoint(String method, String suffix, String body) {}
}
