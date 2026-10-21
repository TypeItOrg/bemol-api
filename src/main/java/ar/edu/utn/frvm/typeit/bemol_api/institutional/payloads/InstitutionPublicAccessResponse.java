package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record InstitutionPublicAccessResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String publicSubdomain,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable String logoUrl) {
  public static @Nullable String logoUrl(final Institution institution) {
    final String version = institution.getLogoVersion();
    return version == null
        ? null
        : "/api/v1/institutions/" + institution.getId() + "/logo?v=" + version;
  }

  public static InstitutionPublicAccessResponse from(final Institution institution) {
    return new InstitutionPublicAccessResponse(
        institution.getId(),
        institution.getName(),
        java.util.Objects.requireNonNull(institution.getPublicSubdomain()),
        logoUrl(institution));
  }
}
