package ar.edu.utn.frvm.typeit.bemol_api.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

class OpenApiPublicRoutesTest {
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Test
  void exposesOnlyTheOpenApiReadPath() {
    assertThat(matchesGetOnlyRoute("/v3/api-docs")).isTrue();
    assertThat(matchesGetOnlyRoute("/v3/api-docs/groups/academic")).isTrue();
    assertThat(matchesGetOnlyRoute("/swagger-ui.html")).isTrue();
    assertThat(matchesGetOnlyRoute("/swagger-ui/index.html")).isTrue();
    assertThat(Arrays.asList(PublicRoutes.PUBLIC_ROUTES)).doesNotContain("/v3/api-docs/**");
    assertThat(Arrays.asList(PublicRoutes.PUBLIC_ROUTES)).doesNotContain("/swagger-ui/**");
  }

  private boolean matchesGetOnlyRoute(final String path) {
    return Arrays.stream(PublicRoutes.GET_ONLY_ROUTES)
        .anyMatch(route -> pathMatcher.match(route, path));
  }
}
