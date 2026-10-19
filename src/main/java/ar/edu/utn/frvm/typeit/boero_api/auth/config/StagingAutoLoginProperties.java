package ar.edu.utn.frvm.typeit.boero_api.auth.config;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auto-login")
public record StagingAutoLoginProperties(
    boolean enabled, @Nullable UUID userId, @Nullable UUID institutionId) {
  public static final String PROFILE = "staging & !prod & !production & !qa & !dev & !test";
}
