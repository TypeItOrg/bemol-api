package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService.PresignedUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record PresignedInstitutionLogoUploadResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uploadUrl,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> headers) {
  public static PresignedInstitutionLogoUploadResponse from(
      final String version, final PresignedUpload presigned) {
    return new PresignedInstitutionLogoUploadResponse(
        version, presigned.url(), presigned.expiresAt(), presigned.headers());
  }
}
