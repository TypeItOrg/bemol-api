package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "studyPlanSpaceId",
      "academicSpaceId",
      "academicLevelId",
      "name",
      "description",
      "type",
      "format",
      "requirementType",
      "displayOrder",
      "approvalMode"
    })
public record AcademicOfferSpaceResponse(
    UUID studyPlanSpaceId,
    UUID academicSpaceId,
    @Schema(nullable = true) @Nullable UUID academicLevelId,
    String name,
    @Schema(nullable = true) @Nullable String description,
    AcademicSpaceType type,
    AcademicSpaceFormat format,
    RequirementType requirementType,
    int displayOrder,
    ApprovalMode approvalMode) {

  public static AcademicOfferSpaceResponse from(final StudyPlanSpace planSpace) {
    final var academicSpace = planSpace.getAcademicSpace();
    final var academicLevel = planSpace.getAcademicLevel();
    return new AcademicOfferSpaceResponse(
        planSpace.getId(),
        academicSpace.getId(),
        academicLevel == null ? null : academicLevel.getId(),
        academicSpace.getName(),
        academicSpace.getDescription(),
        academicSpace.getType(),
        academicSpace.getFormat(),
        planSpace.getRequirementType(),
        planSpace.getDisplayOrder(),
        planSpace.getApprovalMode());
  }
}
