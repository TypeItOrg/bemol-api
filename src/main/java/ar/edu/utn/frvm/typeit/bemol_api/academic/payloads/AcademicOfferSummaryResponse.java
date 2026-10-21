package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "studyPlanId",
      "studyPlanName",
      "studyPlanVersion",
      "effectiveFrom",
      "effectiveTo",
      "trainingPathId",
      "trainingPathName",
      "trainingPathDescription",
      "enrollmentOpen"
    })
public record AcademicOfferSummaryResponse(
    UUID studyPlanId,
    String studyPlanName,
    int studyPlanVersion,
    @Nullable LocalDate effectiveFrom,
    @Schema(nullable = true) @Nullable LocalDate effectiveTo,
    UUID trainingPathId,
    String trainingPathName,
    @Schema(nullable = true) @Nullable String trainingPathDescription,
    boolean enrollmentOpen) {

  public static AcademicOfferSummaryResponse from(
      final StudyPlan plan, final boolean enrollmentOpen) {
    final var trainingPath = plan.getTrainingPath();
    return new AcademicOfferSummaryResponse(
        plan.getId(),
        plan.getName(),
        plan.getVersionNumber(),
        plan.getEffectiveFrom(),
        plan.getEffectiveTo(),
        trainingPath.getId(),
        trainingPath.getName(),
        trainingPath.getDescription(),
        enrollmentOpen);
  }
}
