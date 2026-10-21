package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

@Configuration
public class PathMatcherConfig {

  @Bean
  PathMatcher pathMatcher() {
    return new AntPathMatcher();
  }
}
