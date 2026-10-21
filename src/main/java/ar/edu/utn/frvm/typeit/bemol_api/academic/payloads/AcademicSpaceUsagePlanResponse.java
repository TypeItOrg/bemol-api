package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "studyPlanId",
      "name",
      "versionNumber",
      "trainingPathName",
      "effectiveFrom",
      "effectiveTo",
      "status",
      "placements"
    })
public record AcademicSpaceUsagePlanResponse(
    UUID studyPlanId,
    String name,
    int versionNumber,
    String trainingPathName,
    @Schema(nullable = true) @Nullable LocalDate effectiveFrom,
    @Schema(nullable = true) @Nullable LocalDate effectiveTo,
    StudyPlanStatus status,
    List<AcademicSpaceUsagePlacementResponse> placements) {

  public static AcademicSpaceUsagePlanResponse from(
      final StudyPlan plan, final List<AcademicSpaceUsagePlacementResponse> placements) {
    return new AcademicSpaceUsagePlanResponse(
        plan.getId(),
        plan.getName(),
        plan.getVersionNumber(),
        plan.getTrainingPath().getName(),
        plan.getEffectiveFrom(),
        plan.getEffectiveTo(),
        plan.getStatus(),
        placements);
  }
}
