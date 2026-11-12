package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService.PresignedUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PresignedAttachmentUploadResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID uploadId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uploadUrl,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> headers) {
  public static PresignedAttachmentUploadResponse from(
      final UUID uploadId, final PresignedUpload presigned) {
    return new PresignedAttachmentUploadResponse(
        uploadId, presigned.url(), presigned.expiresAt(), presigned.headers());
  }
}
