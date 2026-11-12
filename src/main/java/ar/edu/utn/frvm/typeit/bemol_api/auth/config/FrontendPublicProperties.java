package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendPublicProperties(
    String publicUrl,
    String institutionalBaseDomain,
    String fallbackUrl,
    String platformPublicUrl,
    String brandLogoUrl) {
  public FrontendPublicProperties(final String publicUrl, final String institutionalBaseDomain) {
    this(
        publicUrl,
        institutionalBaseDomain,
        "http://localhost:3000",
        "http://admin.localhost:3000",
        "");
  }

  public FrontendPublicProperties(
      final String publicUrl, final String institutionalBaseDomain, final String fallbackUrl) {
    this(publicUrl, institutionalBaseDomain, fallbackUrl, "http://admin.localhost:3000", "");
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
    if (!isPlatformOrigin(platform)) {
      throw new IllegalArgumentException(
          "app.frontend.platform-public-url debe ser un origen HTTPS, o HTTP localhost.");
    }
    final URI base = URI.create(publicUrl);
    if (!isCanonicalBase(base)) {
      throw new IllegalArgumentException(
          "app.frontend.public-url debe ser una URL base HTTP(S) canónica.");
    }
    if (institutionalBaseDomain == null) {
      institutionalBaseDomain = "";
    }
    final boolean domainConfigured = !institutionalBaseDomain.isEmpty();
    if (domainConfigured && !isBaseHostDomain(institutionalBaseDomain, base)) {
      throw new IllegalArgumentException(
          "app.frontend.institutional-base-domain debe coincidir con el host público canónico.");
    }

    if (brandLogoUrl == null || brandLogoUrl.isBlank()) {
      brandLogoUrl = "";
    } else {
      URI logo;
      try {
        logo = URI.create(brandLogoUrl);
      } catch (IllegalArgumentException exception) {
        // A malformed URL may contain accidentally pasted credentials.
        throw new IllegalArgumentException(AuthMessages.BRAND_LOGO_URL_INVALID);
      }
      if (!isSecureResource(logo)) {
        throw new IllegalArgumentException(AuthMessages.BRAND_LOGO_URL_INVALID);
      }
    }
  }

  private static boolean isPlatformOrigin(final URI uri) {
    final String host = uri.getHost();
    if (host == null || hasUserInfoQueryOrFragment(uri)) {
      return false;
    }

    final boolean emptyPath = uri.getPath() == null || uri.getPath().isEmpty();
    final boolean secure = "https".equals(uri.getScheme());
    final boolean localhost = host.equals("localhost") || host.endsWith(".localhost");
    final boolean localDevelopment = "http".equals(uri.getScheme()) && localhost;
    final boolean allowedScheme = secure || localDevelopment;

    return emptyPath && allowedScheme;
  }

  private static boolean isCanonicalBase(final URI uri) {
    if (uri.getHost() == null || hasUserInfoQueryOrFragment(uri)) {
      return false;
    }

    final String path = uri.getPath();
    final boolean rootPath = path == null || path.isEmpty() || path.equals("/");
    final boolean httpScheme = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());

    return rootPath && httpScheme;
  }

  private static boolean isBaseHostDomain(final String domain, final URI base) {
    final boolean validDomain = domain.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*");
    final boolean sameHost = domain.equals(base.getHost());

    return validDomain && sameHost;
  }

  private static boolean isSecureResource(final URI uri) {
    final boolean secure = "https".equals(uri.getScheme());

    return secure && uri.getHost() != null && !hasUserInfoQueryOrFragment(uri);
  }

  private static boolean hasUserInfoQueryOrFragment(final URI uri) {
    return uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null;
  }

  public URI platformUri() {
    return URI.create(platformPublicUrl);
  }

  public URI baseUri() {
    return URI.create(publicUrl);
  }
}
