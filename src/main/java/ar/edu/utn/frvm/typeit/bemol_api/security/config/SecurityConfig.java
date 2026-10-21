package ar.edu.utn.frvm.typeit.bemol_api.security.config;

import static ar.edu.utn.frvm.typeit.bemol_api.security.config.PublicRoutes.GET_ONLY_ROUTES;
import static ar.edu.utn.frvm.typeit.bemol_api.security.config.PublicRoutes.PUBLIC_ROUTES;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.ActiveIdentityFilter;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.InstitutionalHostFilter;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticationFilter;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ActiveIdentityService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalHostContext;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final AccessDeniedHandler accessDeniedHandler;
  private final AuthorizationService authorizationService;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final InstitutionalHostContext institutionalHostContext;
  private final ActiveIdentityService activeIdentityService;

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
      throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver)
      throws Exception {

    http.csrf(mappedAbstractHttpConfigurer -> mappedAbstractHttpConfigurer.disable());
    http.formLogin(mappedAbstractHttpConfigurer -> mappedAbstractHttpConfigurer.disable());
    http.httpBasic(mappedAbstractHttpConfigurer -> mappedAbstractHttpConfigurer.disable());
    http.logout(mappedAbstractHttpConfigurer -> mappedAbstractHttpConfigurer.disable());
    http.rememberMe(mappedAbstractHttpConfigurer -> mappedAbstractHttpConfigurer.disable());

    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterAfter(
        new InstitutionalHostFilter(institutionalHostContext, handlerExceptionResolver),
        JwtAuthenticationFilter.class);
    http.addFilterAfter(
        new ActiveIdentityFilter(activeIdentityService, handlerExceptionResolver),
        InstitutionalHostFilter.class);

    http.exceptionHandling(
        exception ->
            exception
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(PUBLIC_ROUTES)
                .permitAll()
                .requestMatchers(HttpMethod.GET, GET_ONLY_ROUTES)
                .permitAll()
                .requestMatchers("/api/*/admin/**")
                .access(
                    (authentication, context) ->
                        new AuthorizationDecision(
                            authorizationService.hasPlatformRole(
                                authentication.get(), PlatformRoleCode.PLATFORM_ADMIN)))
                .anyRequest()
                .authenticated());

    return http.build();
  }
}
