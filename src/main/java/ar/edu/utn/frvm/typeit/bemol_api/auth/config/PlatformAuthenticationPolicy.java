package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformAuthenticationPolicy {
  private final Environment environment;

  @PostConstruct
  void validate() {
    if (!passkeyRequired()
        && !environment.acceptsProfiles(Profiles.of("dev & !staging & !prod & !production"))) {
      throw new IllegalStateException(
          "app.auth.platform.passkey-required solo puede desactivarse en el perfil dev.");
    }
  }

  public boolean passkeyRequired() {
    return environment.getProperty("app.auth.platform.passkey-required", Boolean.class, true);
  }
}
