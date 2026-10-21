package ar.edu.utn.frvm.typeit.bemol_api.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

class InstitutionPublicRoutesTest {

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Test
  @DisplayName("Should match public institution read endpoints")
  void getOnlyRoutes_matchInstitutionReadEndpoints() {
    UUID institutionId = UUID.randomUUID();

    assertThat(matchesGetOnlyRoute("/api/v1/institutions")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/institutions/by-subdomain/cboero")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/institutions/" + institutionId + "/logo")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/institutions/" + institutionId)).isTrue();
  }

  @Test
  @DisplayName("Should not match nested institution routes as public")
  void getOnlyRoutes_doNotMatchNestedInstitutionRoutes() {
    UUID institutionId = UUID.randomUUID();

    assertThat(matchesGetOnlyRoute("/api/v1/institutions/" + institutionId + "/people")).isFalse();
    assertThat(matchesGetOnlyRoute("/api/v1/institutions/" + institutionId + "/people/me"))
        .isFalse();
    assertThat(
            matchesGetOnlyRoute(
                "/api/v1/institutions/"
                    + institutionId
                    + "/people/"
                    + UUID.randomUUID()
                    + "/roles"))
        .isFalse();
  }

  private boolean matchesGetOnlyRoute(String path) {
    return Arrays.stream(PublicRoutes.GET_ONLY_ROUTES)
        .anyMatch(route -> pathMatcher.match(route, path));
  }
}
