package ar.edu.utn.frvm.typeit.boero_api.security.config;

import java.util.stream.Stream;

public final class PublicRoutes {

  private static final String[] AUTH_ROUTES = {
    "/api/v1/auth/register",
    "/api/v1/auth/own-access/activate",
    "/api/v1/auth/email-verification/confirm",
    "/api/v1/auth/email-verification/resend",
    "/api/v1/auth/email-verification/change-email",
    "/api/v1/auth/login/identify",
    "/api/v1/auth/login/password",
    "/api/v1/auth/login/staging",
    "/api/v1/auth/passkeys/authentication/options",
    "/api/v1/auth/passkeys/authentication/verify",
    "/api/v1/auth/passkeys/authentication/discoverable/options",
    "/api/v1/auth/passkeys/authentication/discoverable/verify",
    "/api/v1/auth/password-recovery",
    "/api/v1/auth/password-recovery/reset",
    "/api/v1/auth/refresh",
    "/api/v1/admin/auth/login",
    "/api/v1/admin/auth/refresh"
  };

  private static final String[] INFRASTRUCTURE_ROUTES = {"/actuator/health/**"};

  private static final String[] API_DOCUMENTATION_READ_ROUTES = {
    "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  private static final String[] GREETING_ROUTES = {"/api/v1/greeting/**"};

  private static final String[] INSTITUTION_READ_ROUTES = {
    "/api/v1/institutions",
    "/api/v1/institutions/*",
    "/api/v1/institutions/by-subdomain/*",
    "/api/v1/institutions/*/logo"
  };

  private static final String[] LOCATION_READ_ROUTES = {
    "/api/v1/countries", "/api/v1/countries/**",
    "/api/v1/provinces", "/api/v1/provinces/**",
    "/api/v1/cities"
  };

  public static final String[] PUBLIC_ROUTES =
      Stream.of(AUTH_ROUTES, INFRASTRUCTURE_ROUTES, GREETING_ROUTES)
          .flatMap(Stream::of)
          .toArray(String[]::new);

  public static final String[] GET_ONLY_ROUTES =
      Stream.of(INSTITUTION_READ_ROUTES, LOCATION_READ_ROUTES, API_DOCUMENTATION_READ_ROUTES)
          .flatMap(Stream::of)
          .toArray(String[]::new);
}
