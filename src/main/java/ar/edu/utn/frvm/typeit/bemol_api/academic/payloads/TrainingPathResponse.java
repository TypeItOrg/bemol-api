package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
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
      "active",
      "deletedAt"
    })
public record TrainingPathResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    String name,
    @Schema(nullable = true) @Nullable String description,
    boolean active,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static TrainingPathResponse from(final TrainingPath path) {
    return new TrainingPathResponse(
        path.getId(),
        path.getInstitution().getId(),
        path.getInstitution().getName(),
        path.getName(),
        path.getDescription(),
        path.isActive(),
        path.getDeletedAt());
  }
}
