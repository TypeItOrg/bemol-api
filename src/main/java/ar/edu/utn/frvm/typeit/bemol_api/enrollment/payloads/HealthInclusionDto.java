package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(requiredProperties = {"receivesReasonableAdjustments", "adjustmentDetails"})
public record HealthInclusionDto(
    @Schema(nullable = true) @Nullable Boolean receivesReasonableAdjustments,
    @Schema(nullable = true) @Nullable String adjustmentDetails) {
  public HealthInclusionDto() {
    this(null, null);
  }

  public @Nullable Boolean getReceivesReasonableAdjustments() {
    return receivesReasonableAdjustments;
  }

  public @Nullable String getAdjustmentDetails() {
    return adjustmentDetails;
  }
}
