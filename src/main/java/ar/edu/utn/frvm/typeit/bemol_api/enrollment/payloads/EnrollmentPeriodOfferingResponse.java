package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "studyPlanId",
      "studyPlanName",
      "versionNumber",
      "trainingPathId",
      "trainingPathName",
      "academicLevels",
      "includeUnassigned"
    })
public record EnrollmentPeriodOfferingResponse(
    UUID studyPlanId,
    String studyPlanName,
    int versionNumber,
    UUID trainingPathId,
    String trainingPathName,
    List<AcademicLevelResponse> academicLevels,
    boolean includeUnassigned) {
  public static EnrollmentPeriodOfferingResponse from(final EnrollmentPeriodOffering offering) {
    final var plan = offering.getStudyPlan();
    return new EnrollmentPeriodOfferingResponse(
        plan.getId(),
        plan.getName(),
        plan.getVersionNumber(),
        plan.getTrainingPath().getId(),
        plan.getTrainingPath().getName(),
        offering.getLevels().stream()
            .filter(level -> level.getAcademicLevel() != null)
            .map(level -> AcademicLevelResponse.from(level.getAcademicLevel()))
            .sorted(
                Comparator.comparingInt(
                    mappedAcademicLevelResponse -> mappedAcademicLevelResponse.displayOrder()))
            .toList(),
        offering.includes(null));
  }
}
