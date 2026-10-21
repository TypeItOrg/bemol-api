package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "id",
      "studyPlanId",
      "targetStudyPlanSpaceId",
      "requiredStudyPlanSpaceId",
      "requirementStage",
      "requiredCondition"
    })
public record PrerequisiteResponse(
    UUID id,
    UUID studyPlanId,
    UUID targetStudyPlanSpaceId,
    UUID requiredStudyPlanSpaceId,
    RequirementStage requirementStage,
    RequiredCondition requiredCondition) {

  public static PrerequisiteResponse from(final Prerequisite prerequisite) {
    return new PrerequisiteResponse(
        prerequisite.getId(),
        prerequisite.getStudyPlan().getId(),
        prerequisite.getTargetStudyPlanSpace().getId(),
        prerequisite.getRequiredStudyPlanSpace().getId(),
        prerequisite.getRequirementStage(),
        prerequisite.getRequiredCondition());
  }
}
