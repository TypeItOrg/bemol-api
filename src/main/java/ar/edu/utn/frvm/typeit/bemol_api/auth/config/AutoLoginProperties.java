package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auto-login")
public record AutoLoginProperties(
    boolean enabled, @Nullable UUID userId, @Nullable UUID institutionId) {
  public static final String NON_PRODUCTION_PROFILE = "!prod & !production";
}
