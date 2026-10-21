package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebAuthnDeploymentGuard implements ApplicationRunner {

  private static final String LOCALHOST_RP_ID = "localhost";
  private static final String LOCALHOST_ORIGIN_PREFIX = "http://localhost";

  private final WebAuthnProperties properties;
  private final Environment environment;

  @Override
  public void run(final ApplicationArguments args) {
    if (!requiresProductionConfig()) {
      return;
    }
    rejectLocalhostRpId();
    rejectLocalhostOrigins();
  }

  private boolean requiresProductionConfig() {
    for (final String profile : environment.getActiveProfiles()) {
      if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("test")) {
        return false;
      }
    }
    return true;
  }

  private void rejectLocalhostRpId() {
    final boolean localhost =
        properties.rpId() != null && properties.rpId().equalsIgnoreCase(LOCALHOST_RP_ID);
    if (localhost) {
      throw new IllegalStateException(
          "La propiedad app.webauthn.rp-id no puede ser localhost fuera de dev/test.");
    }
  }

  private void rejectLocalhostOrigins() {
    final List<String> origins =
        properties.allowedOrigins() == null ? List.of() : properties.allowedOrigins();
    final boolean localhost =
        origins.stream()
            .anyMatch(
                origin ->
                    origin != null
                        && origin.toLowerCase(Locale.ROOT).startsWith(LOCALHOST_ORIGIN_PREFIX));
    if (localhost) {
      throw new IllegalStateException(
          "La propiedad app.webauthn.allowed-origins no puede contener orígenes localhost fuera de dev/test.");
    }
  }
}
