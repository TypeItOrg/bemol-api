package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.validation.PublicSubdomainPolicy;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Canonical async URLs use trusted config and the event's institution snapshot, never request
 * headers.
 */
@Service
@RequiredArgsConstructor
public class FrontendAccessUrls {
  private final FrontendPublicProperties properties;

  public String base(final @Nullable String publicSubdomain) {
    PublicSubdomainPolicy.validate(publicSubdomain);
    final var base = properties.baseUri();
    final String hostname =
        publicSubdomain == null || properties.institutionalBaseDomain().isEmpty()
            ? base.getHost()
            : publicSubdomain + "." + properties.institutionalBaseDomain();
    return base.getScheme() + "://" + hostname + (base.getPort() == -1 ? "" : ":" + base.getPort());
  }

  public String tokenUrl(
      final @Nullable String publicSubdomain, final String path, final String token) {
    return UriComponentsBuilder.fromUriString(base(publicSubdomain))
        .path(path)
        .queryParam("token", token)
        .build()
        .encode()
        .toUriString();
  }

  public String brandLogoUrl() {
    return base(null) + "/brand/logo-email.png";
  }

  public String logoUrl(
      final @Nullable String publicSubdomain,
      final @Nullable UUID institutionId,
      final @Nullable String logoVersion) {
    if (institutionId == null || logoVersion == null || logoVersion.isBlank()) {
      return brandLogoUrl();
    }
    return UriComponentsBuilder.fromUriString(base(publicSubdomain))
        .path("/api/public/institutions/{institutionId}/logo")
        .queryParam("v", logoVersion)
        .queryParam("format", "png")
        .buildAndExpand(institutionId)
        .encode()
        .toUriString();
  }
}
