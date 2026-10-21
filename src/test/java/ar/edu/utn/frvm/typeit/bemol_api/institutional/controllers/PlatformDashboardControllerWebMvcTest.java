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
import ar.edu.utn.frvm.typeit.bemol_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.MonthlyInstitutionRegistrationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.PlatformDashboardResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.PlatformDashboardSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.RecentInstitutionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetPlatformDashboardUseCase;
import java.util.List;
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

@WebMvcTest(PlatformDashboardController.class)
@Import({RoleAuthorizationAspect.class, GlobalExceptionHandler.class, WebConfig.class})
@EnableAspectJAutoProxy
@AutoConfigureMockMvc(addFilters = false)
class PlatformDashboardControllerWebMvcTest {

  private static final UUID PLATFORM_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private GetPlatformDashboardUseCase getPlatformDashboardUseCase;

  @Test
  @DisplayName("Should forbid unauthenticated dashboard access")
  void get_returnsForbiddenWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should forbid institutional account dashboard access")
  void get_returnsForbiddenForInstitutionalAccount() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(
            institutionalPrincipal(UUID.randomUUID(), INSTITUTION_ID), "");
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(false);

    mockMvc
        .perform(get("/api/v1/admin/dashboard").principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should return dashboard for platform administrator")
  void get_returnsDashboardForPlatformAdministrator() throws Exception {
    final var authentication =
        new TestingAuthenticationToken(platformPrincipal(PLATFORM_ACCOUNT_ID), "");
    when(authorizationService.hasPlatformRole(any(), eq(PlatformRoleCode.PLATFORM_ADMIN)))
        .thenReturn(true);
    when(getPlatformDashboardUseCase.execute()).thenReturn(dashboardResponse());

    mockMvc
        .perform(get("/api/v1/admin/dashboard").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.institutions").value(12))
        .andExpect(jsonPath("$.summary.usersWithAccess").value(31))
        .andExpect(jsonPath("$.institutionRegistrations[0].year").value(2026))
        .andExpect(jsonPath("$.institutionRegistrations[0].month").value(7))
        .andExpect(jsonPath("$.recentInstitutions[0].id").value(INSTITUTION_ID.toString()));
  }

  private static PlatformDashboardResponse dashboardResponse() {
    return PlatformDashboardResponse.builder()
        .summary(
            PlatformDashboardSummaryResponse.builder()
                .institutions(12)
                .activeInstitutions(10)
                .inactiveInstitutions(2)
                .people(45)
                .usersWithAccess(31)
                .build())
        .institutionRegistrations(
            List.of(
                MonthlyInstitutionRegistrationResponse.builder()
                    .year(2026)
                    .month(7)
                    .count(2)
                    .build()))
        .recentInstitutions(
            List.of(
                RecentInstitutionResponse.builder()
                    .id(INSTITUTION_ID)
                    .name("Instituto Boero")
                    .city("Villa María")
                    .province("Córdoba")
                    .active(true)
                    .createdAt(
                        java.time.LocalDateTime.of(2026, 7, 10, 12, 0)
                            .toInstant(java.time.ZoneOffset.UTC))
                    .build()))
        .build();
  }
}
