package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentReviewStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "requirementId",
      "originalFileName",
      "size",
      "contentType",
      "createdAt",
      "versionStatus",
      "reviewStatus",
      "uploadedBy",
      "uploaderType",
      "reviewedBy",
      "reviewerType",
      "reviewedAt",
      "observation",
      "storagePath"
    })
public record EnrollmentAttachmentResponse(
    UUID id,
    UUID requirementId,
    String originalFileName,
    Long size,
    String contentType,
    Instant createdAt,
    DocumentVersionStatus versionStatus,
    DocumentReviewStatus reviewStatus,
    @Schema(nullable = true) @Nullable UUID uploadedBy,
    String uploaderType,
    @Schema(nullable = true) @Nullable UUID reviewedBy,
    @Schema(nullable = true) @Nullable String reviewerType,
    @Schema(nullable = true) @Nullable Instant reviewedAt,
    @Schema(nullable = true) @Nullable String observation,
    @Schema(nullable = true) @Nullable String storagePath) {
  public static EnrollmentAttachmentResponse from(EnrollmentAttachment value) {
    return new EnrollmentAttachmentResponse(
        value.getId(),
        value.getRequirement().getId(),
        value.getOriginalFileName(),
        value.getFileSize(),
        value.getContentType(),
        value.getCreatedAt(),
        value.getVersionStatus(),
        value.getReviewStatus(),
        value.getUploadedBy(),
        value.getUploaderType(),
        value.getReviewedBy(),
        value.getReviewerType(),
        value.getReviewedAt(),
        value.getObservation(),
        null);
  }
}
