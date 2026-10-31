package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformHostRequiredException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;

class PlatformHostBoundaryTest {
  private final InstitutionalHostContext context =
      new InstitutionalHostContext(
          new FrontendPublicProperties("http://localhost:3000", "localhost"),
          mock(InstitutionRepository.class));

  @Test
  void platformRequiresItsHostEvenOnSharedEndpoints() {
    final var principal = AuthTestData.platformPrincipal(UUID.randomUUID());
    final var authentication = new TestingAuthenticationToken(principal, "");
    final var request = new MockHttpServletRequest("GET", "/api/v1/institutions");
    assertThatThrownBy(() -> context.requirePlatformHost(request, authentication))
        .isInstanceOf(PlatformHostRequiredException.class);
    request.addHeader("X-Institutional-Host", "admin.localhost:3000");
    context.requirePlatformHost(request, authentication);
    assertThat(context.resolve("admin.localhost:3000")).isEmpty();
  }

  @Test
  void sharedHttpFixturesKeepRoleTestsOnTheCorrectFrontendWithoutOverwritingExplicitHosts() {
    final var principal = AuthTestData.platformPrincipal(UUID.randomUUID());
    final var request = new MockHttpServletRequest("GET", "/api/v1/institutions");
    AuthTestData.authentication(new TestingAuthenticationToken(principal, ""))
        .postProcessRequest(request);
    assertThat(request.getHeader("X-Institutional-Host")).isEqualTo("admin.localhost:3000");
    final var anonymous = new MockHttpServletRequest("GET", "/api/v1/admin/auth/me");
    AuthTestData.anonymous().postProcessRequest(anonymous);
    assertThat(anonymous.getHeader("X-Institutional-Host")).isEqualTo("admin.localhost:3000");
    request.removeHeader("X-Institutional-Host");
    request.addHeader("X-Institutional-Host", "wrong.test");
    AuthTestData.authentication(new TestingAuthenticationToken(principal, ""))
        .postProcessRequest(request);
    assertThat(request.getHeader("X-Institutional-Host")).isEqualTo("wrong.test");
  }
}
