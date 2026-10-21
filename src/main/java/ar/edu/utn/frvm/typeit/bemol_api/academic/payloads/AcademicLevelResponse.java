package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"id", "studyPlanId", "name", "displayOrder", "description"})
public record AcademicLevelResponse(
    UUID id,
    UUID studyPlanId,
    String name,
    int displayOrder,
    @Schema(nullable = true) @Nullable String description) {

  public static AcademicLevelResponse from(final AcademicLevel level) {
    return new AcademicLevelResponse(
        level.getId(),
        level.getStudyPlan().getId(),
        level.getName(),
        level.getDisplayOrder(),
        level.getDescription());
  }
}
