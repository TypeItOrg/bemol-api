package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
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
public record InstrumentResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    String name,
    @Schema(nullable = true) @Nullable String description,
    boolean active,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static InstrumentResponse from(final Instrument instrument) {
    return new InstrumentResponse(
        instrument.getId(),
        instrument.getInstitution().getId(),
        instrument.getInstitution().getName(),
        instrument.getName(),
        instrument.getDescription(),
        instrument.isActive(),
        instrument.getDeletedAt());
  }
}
