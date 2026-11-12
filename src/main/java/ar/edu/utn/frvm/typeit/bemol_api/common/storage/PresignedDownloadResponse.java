package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PresignedDownloadResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {
  public static PresignedDownloadResponse from(final StorageService.PresignedDownload presigned) {
    return new PresignedDownloadResponse(presigned.url(), presigned.expiresAt());
  }
}
