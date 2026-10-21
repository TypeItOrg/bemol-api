package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(requiredProperties = {"studyPlanSpaceInstrumentIds"})
public record InstrumentSelectionDto(
    @Schema(nullable = true) @Nullable Map<UUID, UUID> studyPlanSpaceInstrumentIds) {
  public InstrumentSelectionDto() {
    this(new HashMap<>());
  }

  public InstrumentSelectionDto {
    studyPlanSpaceInstrumentIds =
        studyPlanSpaceInstrumentIds == null ? new HashMap<>() : studyPlanSpaceInstrumentIds;
  }

  public @Nullable Map<UUID, UUID> getStudyPlanSpaceInstrumentIds() {
    return studyPlanSpaceInstrumentIds;
  }
}
