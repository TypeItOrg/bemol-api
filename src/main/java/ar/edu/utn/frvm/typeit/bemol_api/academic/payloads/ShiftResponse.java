package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
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
public record ShiftResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    String name,
    @Schema(nullable = true) @Nullable String description,
    boolean active,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static ShiftResponse from(final Shift shift) {
    return new ShiftResponse(
        shift.getId(),
        shift.getInstitution().getId(),
        shift.getInstitution().getName(),
        shift.getName(),
        shift.getDescription(),
        shift.isActive(),
        shift.getDeletedAt());
  }
}
