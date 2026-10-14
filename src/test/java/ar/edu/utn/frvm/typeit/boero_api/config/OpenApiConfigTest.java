package ar.edu.utn.frvm.typeit.boero_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
  @Test
  void usesThePublicVersionPrefixInDocumentedPaths() {
    final PathItem institutions = new PathItem();
    final PathItem health = new PathItem();
    final PathItem otherVersion = new PathItem();
    final PathItem root = new PathItem();
    final OpenAPI openApi =
        new OpenAPI()
            .paths(
                new Paths()
                    .addPathItem("/api/1/institutions", institutions)
                    .addPathItem("/api/1", root)
                    .addPathItem("/api/10/institutions", otherVersion)
                    .addPathItem("/actuator/health", health));

    new OpenApiConfig().publicApiVersionPaths().customise(openApi);

    assertThat(openApi.getPaths())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "/api/v1/institutions",
                institutions,
                "/api/v1",
                root,
                "/api/10/institutions",
                otherVersion,
                "/actuator/health",
                health));
  }

  @Test
  void toleratesADocumentWithoutPaths() {
    final OpenAPI openApi = new OpenAPI();

    new OpenApiConfig().publicApiVersionPaths().customise(openApi);

    assertThat(openApi.getPaths()).isNull();
  }
}
