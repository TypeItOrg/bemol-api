package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"id", "name", "type", "format", "studyPlanSpaceId", "instrumental"})
public record CourseSpaceOptionResponse(
    UUID id,
    String name,
    String type,
    String format,
    UUID studyPlanSpaceId,
    @Schema(nullable = true) @Nullable String academicLevelName,
    boolean instrumental) {

  public static CourseSpaceOptionResponse from(final StudyPlanSpace space) {
    final var academicSpace = space.getAcademicSpace();
    final var academicLevel = space.getAcademicLevel();
    return new CourseSpaceOptionResponse(
        academicSpace.getId(),
        academicSpace.getName(),
        academicSpace.getType().name(),
        academicSpace.getFormat().name(),
        space.getId(),
        academicLevel == null ? null : academicLevel.getName(),
        academicSpace.isInstrumental());
  }
}
