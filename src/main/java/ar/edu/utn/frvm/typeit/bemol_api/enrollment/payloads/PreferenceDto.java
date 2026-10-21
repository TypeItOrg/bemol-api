package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(
    requiredProperties = {"preferredShift", "allowsImageUse", "isReenrolling", "previousTeacher"})
public record PreferenceDto(
    @Schema(nullable = true) @Nullable String preferredShift,
    @Schema(nullable = true) @Nullable Boolean allowsImageUse,
    @Schema(nullable = true) @Nullable Boolean isReenrolling,
    @Schema(nullable = true) @Nullable String previousTeacher) {
  public PreferenceDto() {
    this(null, null, null, null);
  }

  public @Nullable String getPreferredShift() {
    return preferredShift;
  }

  public @Nullable Boolean getAllowsImageUse() {
    return allowsImageUse;
  }

  public @Nullable Boolean getIsReenrolling() {
    return isReenrolling;
  }

  public @Nullable String getPreviousTeacher() {
    return previousTeacher;
  }
}
