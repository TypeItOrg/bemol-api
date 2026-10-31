package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendPublicProperties(
    String publicUrl,
    String institutionalBaseDomain,
    String fallbackUrl,
    String platformPublicUrl) {
  public FrontendPublicProperties(final String publicUrl, final String institutionalBaseDomain) {
    this(
        publicUrl, institutionalBaseDomain, "http://localhost:3000", "http://admin.localhost:3000");
  }

  public FrontendPublicProperties(
      final String publicUrl, final String institutionalBaseDomain, final String fallbackUrl) {
    this(publicUrl, institutionalBaseDomain, fallbackUrl, "http://admin.localhost:3000");
  }

  @ConstructorBinding
  public FrontendPublicProperties {
    if (publicUrl == null || publicUrl.isBlank()) {
      publicUrl =
          fallbackUrl == null || fallbackUrl.isBlank() ? "http://localhost:3000" : fallbackUrl;
    }
    if (platformPublicUrl == null || platformPublicUrl.isBlank()) {
      platformPublicUrl = "http://admin.localhost:3000";
    }
    final URI platform = URI.create(platformPublicUrl);
    final String hostname = platform.getHost();
    if (hostname == null
        || platform.getUserInfo() != null
        || platform.getQuery() != null
        || platform.getFragment() != null
        || platform.getPath() != null && !platform.getPath().isEmpty()
        || !("https".equals(platform.getScheme())
            || "http".equals(platform.getScheme())
                && (hostname.equals("localhost") || hostname.endsWith(".localhost")))) {
      throw new IllegalArgumentException(
          "app.frontend.platform-public-url debe ser un origen HTTPS, o HTTP localhost.");
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

  public URI platformUri() {
    return URI.create(platformPublicUrl);
  }

  public URI baseUri() {
    return URI.create(publicUrl);
  }
}
