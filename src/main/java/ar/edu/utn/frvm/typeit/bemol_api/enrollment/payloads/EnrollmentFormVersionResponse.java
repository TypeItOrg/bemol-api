package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "formId",
      "institutionId",
      "versionNumber",
      "status",
      "revision",
      "publishedAt",
      "archivedAt",
      "definition"
    })
public record EnrollmentFormVersionResponse(
    UUID id,
    UUID formId,
    UUID institutionId,
    int versionNumber,
    EnrollmentFormVersionStatus status,
    long revision,
    @Schema(nullable = true) @Nullable Instant publishedAt,
    @Schema(nullable = true) @Nullable Instant archivedAt,
    EnrollmentFormDefinition definition) {
  public static EnrollmentFormVersionResponse from(final EnrollmentFormVersion value) {
    return new EnrollmentFormVersionResponse(
        value.getId(),
        value.getForm().getId(),
        value.getInstitution().getId(),
        value.getVersionNumber(),
        value.getStatus(),
        value.getRevision(),
        value.getPublishedAt(),
        value.getArchivedAt(),
        value.getDefinition());
  }
}
