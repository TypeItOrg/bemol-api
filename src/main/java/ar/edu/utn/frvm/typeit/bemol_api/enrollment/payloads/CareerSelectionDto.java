package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(requiredProperties = {"trainingPathId"})
public record CareerSelectionDto(@Schema(nullable = true) @Nullable UUID trainingPathId) {
  public CareerSelectionDto() {
    this(null);
  }

  public @Nullable UUID getTrainingPathId() {
    return trainingPathId;
  }
}
