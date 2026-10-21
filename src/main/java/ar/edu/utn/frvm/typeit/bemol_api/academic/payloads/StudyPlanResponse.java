package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "institutionName",
      "trainingPathId",
      "trainingPathName",
      "name",
      "effectiveFrom",
      "effectiveTo",
      "status",
      "previousVersionId",
      "versionNumber",
      "deletedAt"
    })
public record StudyPlanResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    UUID trainingPathId,
    String trainingPathName,
    String name,
    @Schema(nullable = true) @Nullable LocalDate effectiveFrom,
    @Schema(nullable = true) @Nullable LocalDate effectiveTo,
    StudyPlanStatus status,
    @Schema(nullable = true) @Nullable UUID previousVersionId,
    int versionNumber,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static StudyPlanResponse from(final StudyPlan plan) {
    return new StudyPlanResponse(
        plan.getId(),
        plan.getInstitution().getId(),
        plan.getInstitution().getName(),
        plan.getTrainingPath().getId(),
        plan.getTrainingPath().getName(),
        plan.getName(),
        plan.getEffectiveFrom(),
        plan.getEffectiveTo(),
        plan.getStatus(),
        plan.getPreviousVersion() == null ? null : plan.getPreviousVersion().getId(),
        plan.getVersionNumber(),
        plan.getDeletedAt());
  }
}
