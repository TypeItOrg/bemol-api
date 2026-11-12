package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService.PresignedUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record PresignedGuardianAttachmentUploadResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String storageKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uploadUrl,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> headers) {
  public static PresignedGuardianAttachmentUploadResponse from(
      final String storageKey, final PresignedUpload presigned) {
    return new PresignedGuardianAttachmentUploadResponse(
        storageKey, presigned.url(), presigned.expiresAt(), presigned.headers());
  }
}
