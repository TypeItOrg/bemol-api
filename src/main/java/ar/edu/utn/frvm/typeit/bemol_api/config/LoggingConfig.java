package ar.edu.utn.frvm.typeit.bemol_api.config;

import ar.edu.utn.frvm.typeit.bemol_api.common.logging.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingConfig {

  @Bean
  public RequestLoggingFilter requestLoggingFilter() {
    return new RequestLoggingFilter();
  }

  @Bean
  public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
      final RequestLoggingFilter filter) {
    final FilterRegistrationBean<RequestLoggingFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
