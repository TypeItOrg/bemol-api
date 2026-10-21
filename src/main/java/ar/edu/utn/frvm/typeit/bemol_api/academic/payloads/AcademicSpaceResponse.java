package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "institutionName",
      "name",
      "description",
      "type",
      "format",
      "instrumental",
      "active",
      "deletedAt"
    })
public record AcademicSpaceResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    String name,
    @Schema(nullable = true) @Nullable String description,
    AcademicSpaceType type,
    AcademicSpaceFormat format,
    boolean instrumental,
    boolean active,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static AcademicSpaceResponse from(final AcademicSpace space) {
    return new AcademicSpaceResponse(
        space.getId(),
        space.getInstitution().getId(),
        space.getInstitution().getName(),
        space.getName(),
        space.getDescription(),
        space.getType(),
        space.getFormat(),
        space.isInstrumental(),
        space.isActive(),
        space.getDeletedAt());
  }
}
