package ar.edu.utn.frvm.typeit.bemol_api.support;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import java.time.Duration;
import java.util.UUID;

public final class AuthTestData {

  public static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";

  private AuthTestData() {}

  public static org.springframework.test.web.servlet.request.RequestPostProcessor authentication(
      final org.springframework.security.core.Authentication authentication) {
    return request -> {
      final var result =
          org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
              .authentication(authentication)
              .postProcessRequest(request);
      if (authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount
          || result.getRequestURI().startsWith("/api/v1/admin/")) {
        bindPlatformHost(result);
      }
      return result;
    };
  }

  public static org.springframework.test.web.servlet.request.RequestPostProcessor anonymous() {
    return request -> {
      final var result =
          org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
              .anonymous()
              .postProcessRequest(request);
      if (result.getRequestURI().startsWith("/api/v1/admin/")) {
        bindPlatformHost(result);
      }
      return result;
    };
  }

  private static void bindPlatformHost(
      final org.springframework.mock.web.MockHttpServletRequest request) {
    if (request.getHeader("X-Institutional-Host") == null) {
      request.addHeader("X-Institutional-Host", "admin.localhost:3000");
    }
  }

  public static JwtProperties jwtProperties() {
    return new JwtProperties(
        JWT_SECRET, Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30));
  }

  public static JwtAuthenticatedUser institutionalPrincipal(UUID userId, UUID institutionId) {
    return JwtAuthenticatedUser.builder()
        .userId(userId)
        .personId(UUID.randomUUID())
        .documentNumber("12345678")
        .institutionId(institutionId)
        .sessionId(UUID.randomUUID())
        .tokenId("jti")
        .build();
  }

  public static JwtAuthenticatedUser institutionalPrincipal(
      UUID userId, UUID institutionId, UUID sessionId) {
    return JwtAuthenticatedUser.builder()
        .userId(userId)
        .personId(UUID.randomUUID())
        .documentNumber("12345678")
        .institutionId(institutionId)
        .sessionId(sessionId)
        .tokenId("token-id")
        .build();
  }

  public static JwtAuthenticatedPlatformAccount platformPrincipal(UUID platformAccountId) {
    return JwtAuthenticatedPlatformAccount.builder()
        .platformAccountId(platformAccountId)
        .email("admin@plataforma.com")
        .sessionId(UUID.randomUUID())
        .tokenId("jti")
        .build();
  }
}
