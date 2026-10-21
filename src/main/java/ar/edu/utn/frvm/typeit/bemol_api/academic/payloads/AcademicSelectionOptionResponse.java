package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "name",
      "year",
      "trainingPathId",
      "trainingPathName",
      "versionNumber",
      "type",
      "format",
      "instrumental"
    })
public record AcademicSelectionOptionResponse(
    UUID id,
    String name,
    @Schema(nullable = true) @Nullable Integer year,
    @Schema(nullable = true) @Nullable UUID trainingPathId,
    @Schema(nullable = true) @Nullable String trainingPathName,
    @Schema(nullable = true) @Nullable Integer versionNumber,
    @Schema(nullable = true) @Nullable String type,
    @Schema(nullable = true) @Nullable String format,
    @Schema(nullable = true) @Nullable Boolean instrumental) {}
