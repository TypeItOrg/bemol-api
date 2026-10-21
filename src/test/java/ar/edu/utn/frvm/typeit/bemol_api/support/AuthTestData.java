package ar.edu.utn.frvm.typeit.bemol_api.support;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import java.time.Duration;
import java.util.UUID;

public final class AuthTestData {

  public static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";

  private AuthTestData() {}

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
