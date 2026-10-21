package ar.edu.utn.frvm.typeit.bemol_api.authorization.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.platform-admin")
public record PlatformAdminProperties(String email, String password, String name, String lastName) {

  private static final String DEFAULT_NAME = "Administrador";
  private static final String DEFAULT_LAST_NAME = "Plataforma";

  public @Nullable String resolvedEmail() {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim().toLowerCase();
  }

  public String resolvedName() {
    if (name == null || name.isBlank()) {
      return DEFAULT_NAME;
    }
    return name.trim();
  }

  public String resolvedLastName() {
    if (lastName == null || lastName.isBlank()) {
      return DEFAULT_LAST_NAME;
    }
    return lastName.trim();
  }

  public boolean hasPassword() {
    return password != null && !password.isBlank();
  }
}
