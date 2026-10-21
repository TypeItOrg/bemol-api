package ar.edu.utn.frvm.typeit.bemol_api.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

class LocationPublicRoutesTest {

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Test
  @DisplayName("Should match public location read endpoints")
  void getOnlyRoutes_matchLocationReadEndpoints() {
    UUID provinceId = UUID.randomUUID();
    UUID countryId = UUID.randomUUID();

    assertThat(matchesGetOnlyRoute("/api/v1/countries")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/countries/" + countryId + "/provinces")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/provinces")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/provinces/" + provinceId)).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/provinces/" + provinceId + "/cities")).isTrue();
    assertThat(matchesGetOnlyRoute("/api/v1/cities")).isTrue();
  }

  private boolean matchesGetOnlyRoute(String path) {
    return Arrays.stream(PublicRoutes.GET_ONLY_ROUTES)
        .anyMatch(route -> pathMatcher.match(route, path));
  }
}
