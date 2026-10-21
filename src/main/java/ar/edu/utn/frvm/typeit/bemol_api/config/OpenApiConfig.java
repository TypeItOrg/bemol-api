package ar.edu.utn.frvm.typeit.bemol_api.config;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  private static final String DOCUMENTED_VERSION_PREFIX = "/api/" + Version.V1;
  private static final String PUBLIC_VERSION_PREFIX = "/api/v" + Version.V1;

  @Bean
  OpenApiCustomizer publicApiVersionPaths() {
    return OpenApiConfig::usePublicVersionPaths;
  }

  private static void usePublicVersionPaths(final OpenAPI openApi) {
    if (openApi.getPaths() == null) {
      return;
    }

    final Paths publicPaths = new Paths();
    openApi
        .getPaths()
        .forEach((path, pathItem) -> publicPaths.addPathItem(toPublicVersionPath(path), pathItem));
    openApi.setPaths(publicPaths);
  }

  private static String toPublicVersionPath(final String path) {
    if (path.equals(DOCUMENTED_VERSION_PREFIX)
        || path.startsWith(DOCUMENTED_VERSION_PREFIX + "/")) {
      return PUBLIC_VERSION_PREFIX + path.substring(DOCUMENTED_VERSION_PREFIX.length());
    }
    return path;
  }
}
