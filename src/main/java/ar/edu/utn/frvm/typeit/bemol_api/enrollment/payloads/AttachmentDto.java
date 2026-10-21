package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(
    requiredProperties = {
      "id",
      "requirementId",
      "originalFileName",
      "storagePath",
      "contentType",
      "fileSize",
      "createdAt"
    })
public record AttachmentDto(
    @Schema(nullable = true) @Nullable UUID id,
    @Schema(nullable = true) @Nullable UUID requirementId,
    @Schema(nullable = true) @Nullable String originalFileName,
    @Schema(nullable = true) @Nullable String storagePath,
    @Schema(nullable = true) @Nullable String contentType,
    @Schema(nullable = true) @Nullable Long fileSize,
    @Schema(nullable = true) @Nullable Instant createdAt) {
  public AttachmentDto() {
    this(null, null, null, null, null, null, null);
  }

  public @Nullable UUID getId() {
    return id;
  }

  public @Nullable UUID getRequirementId() {
    return requirementId;
  }

  public @Nullable String getOriginalFileName() {
    return originalFileName;
  }

  public @Nullable String getStoragePath() {
    return storagePath;
  }

  public @Nullable String getContentType() {
    return contentType;
  }

  public @Nullable Long getFileSize() {
    return fileSize;
  }

  public @Nullable Instant getCreatedAt() {
    return createdAt;
  }
}
