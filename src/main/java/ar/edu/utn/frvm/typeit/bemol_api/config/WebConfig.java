package ar.edu.utn.frvm.typeit.bemol_api.config;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.UnversionedRestController;
import java.util.function.Predicate;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private static final String APPLICATION_PACKAGE = "ar.edu.utn.frvm.typeit.bemol_api";
  private static final Predicate<Class<?>> VERSIONED_REST_CONTROLLERS =
      HandlerTypePredicate.forAnnotation(RestController.class)
          .and(WebConfig::isVersionedRestController);

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/api/{version}", VERSIONED_REST_CONTROLLERS);
  }

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.usePathSegment(1, WebConfig::isVersionedApiPath);
    configurer.setVersionRequired(false);
  }

  private static boolean isVersionedRestController(final Class<?> controllerType) {
    return controllerType.getPackageName().startsWith(APPLICATION_PACKAGE)
        && !AnnotatedElementUtils.hasAnnotation(controllerType, UnversionedRestController.class);
  }

  private static boolean isVersionedApiPath(final RequestPath requestPath) {
    return requestPath.pathWithinApplication().value().startsWith("/api/");
  }
}
