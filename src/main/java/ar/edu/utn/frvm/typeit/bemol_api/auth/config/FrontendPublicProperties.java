package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendPublicProperties(
    String publicUrl, String institutionalBaseDomain, String fallbackUrl) {
  public FrontendPublicProperties(final String publicUrl, final String institutionalBaseDomain) {
    this(publicUrl, institutionalBaseDomain, "http://localhost:3000");
  }

  @ConstructorBinding
  public FrontendPublicProperties {
    if (publicUrl == null || publicUrl.isBlank()) {
      publicUrl =
          fallbackUrl == null || fallbackUrl.isBlank() ? "http://localhost:3000" : fallbackUrl;
    }
    final URI base = URI.create(publicUrl);
    if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme()))
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null
        || (base.getPath() != null && !base.getPath().isEmpty() && !base.getPath().equals("/"))) {
      throw new IllegalArgumentException(
          "app.frontend.public-url debe ser una URL base HTTP(S) canónica.");
    }
    if (institutionalBaseDomain == null) {
      institutionalBaseDomain = "";
    }
    if (!institutionalBaseDomain.isEmpty()
        && (!institutionalBaseDomain.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")
            || !institutionalBaseDomain.equals(base.getHost()))) {
      throw new IllegalArgumentException(
          "app.frontend.institutional-base-domain debe coincidir con el host público canónico.");
    }
  }

  public URI baseUri() {
    return URI.create(publicUrl);
  }
}
