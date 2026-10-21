package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.institutional-dev-user")
public record InstitutionalDevUserProperties(
    UUID institutionId, String documentNumber, String password, String name, String lastName) {

  private static final String DEFAULT_NAME = "Ana";
  private static final String DEFAULT_LAST_NAME = "Garcia";

  public @Nullable String resolvedDocumentNumber() {
    if (documentNumber == null || documentNumber.isBlank()) {
      return null;
    }
    return documentNumber.trim();
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
