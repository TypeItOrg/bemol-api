package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.webauthn")
public record WebAuthnProperties(
    String rpId,
    String rpName,
    List<String> allowedOrigins,
    Duration challengeTtl,
    Duration loginAttemptTtl,
    int maxPasskeys,
    Duration recentAuthTtl) {

  public WebAuthnProperties {
    if (rpId == null || rpId.isBlank()) {
      throw new IllegalArgumentException("La propiedad app.webauthn.rp-id es obligatoria.");
    }
    if (rpName == null || rpName.isBlank()) {
      throw new IllegalArgumentException("La propiedad app.webauthn.rp-name es obligatoria.");
    }
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      throw new IllegalArgumentException(
          "La propiedad app.webauthn.allowed-origins es obligatoria.");
    }
    if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()) {
      throw new IllegalArgumentException(
          "La propiedad app.webauthn.challenge-ttl debe ser positiva.");
    }
    if (loginAttemptTtl == null || loginAttemptTtl.isZero() || loginAttemptTtl.isNegative()) {
      throw new IllegalArgumentException(
          "La propiedad app.webauthn.login-attempt-ttl debe ser positiva.");
    }
    if (maxPasskeys <= 0) {
      throw new IllegalArgumentException(
          "La propiedad app.webauthn.max-passkeys debe ser positiva.");
    }
    if (recentAuthTtl == null || recentAuthTtl.isZero() || recentAuthTtl.isNegative()) {
      throw new IllegalArgumentException(
          "La propiedad app.webauthn.recent-auth-ttl debe ser positiva.");
    }
    allowedOrigins = List.copyOf(allowedOrigins);
  }
}
