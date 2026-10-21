package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PlatformPersonSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListPlatformPeopleUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(PlatformPeopleController.class)
@Import({RoleAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PlatformPeopleControllerWebMvcTest {

  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PERSON_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListPlatformPeopleUseCase listPlatformPeopleUseCase;

  @Test
  @DisplayName("Should return filtered people for platform administrator")
  void list_returnsFilteredPeopleForPlatformAdministrator() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(true);
    when(listPlatformPeopleUseCase.execute(
            eq("ana"), eq(INSTITUTION_ID), eq(SystemRoleCode.TEACHER), any(Pageable.class)))
        .thenReturn(
            PaginatedResponse.<PlatformPersonSummaryResponse>builder()
                .items(List.of(person()))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(
            get("/api/v1/admin/people")
                .param("search", "ana")
                .param("institutionId", INSTITUTION_ID.toString())
                .param("roleCode", SystemRoleCode.TEACHER.name())
                .param("size", "10")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(PERSON_ID.toString()))
        .andExpect(jsonPath("$.items[0].institutionId").value(INSTITUTION_ID.toString()))
        .andExpect(jsonPath("$.items[0].institutionName").value("Instituto Boero"))
        .andExpect(jsonPath("$.items[0].roles[0].roleCode").value("TEACHER"));
  }

  @Test
  @DisplayName("Should forbid institutional accounts")
  void list_forbidsInstitutionalAccounts() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(false);

    mockMvc
        .perform(get("/api/v1/admin/people").principal(authentication))
        .andExpect(status().isForbidden());
  }

  private static PlatformPersonSummaryResponse person() {
    return new PlatformPersonSummaryResponse(
        PERSON_ID,
        "Ana",
        "García",
        "12345678",
        "ana@boero.edu.ar",
        null,
        INSTITUTION_ID,
        "Instituto Boero",
        List.of(new PersonSummaryResponse.PersonRoleSummaryResponse("TEACHER", "Docente")));
  }
}
