package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "studyPlanId",
      "academicSpaceId",
      "academicSpaceName",
      "academicLevelId",
      "academicLevelName",
      "requirementType",
      "displayOrder",
      "approvalMode",
      "requiresInstrument",
      "allowedInstruments"
    })
public record StudyPlanSpaceResponse(
    UUID id,
    UUID studyPlanId,
    UUID academicSpaceId,
    String academicSpaceName,
    @Schema(nullable = true) @Nullable UUID academicLevelId,
    @Schema(nullable = true) @Nullable String academicLevelName,
    RequirementType requirementType,
    int displayOrder,
    ApprovalMode approvalMode,
    boolean requiresInstrument,
    List<StudyPlanSpaceInstrumentOptionResponse> allowedInstruments) {

  public static StudyPlanSpaceResponse from(final StudyPlanSpace space) {
    return from(space, List.of());
  }

  public static StudyPlanSpaceResponse from(
      final StudyPlanSpace space,
      final List<StudyPlanSpaceInstrumentOptionResponse> allowedInstruments) {
    final var level = space.getAcademicLevel();

    return new StudyPlanSpaceResponse(
        space.getId(),
        space.getStudyPlan().getId(),
        space.getAcademicSpace().getId(),
        space.getAcademicSpace().getName(),
        level == null ? null : level.getId(),
        level == null ? null : level.getName(),
        space.getRequirementType(),
        space.getDisplayOrder(),
        space.getApprovalMode(),
        space.getAcademicSpace().isInstrumental() || !allowedInstruments.isEmpty(),
        allowedInstruments);
  }
}
