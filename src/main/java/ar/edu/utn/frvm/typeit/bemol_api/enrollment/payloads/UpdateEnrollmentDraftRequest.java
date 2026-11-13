package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record UpdateEnrollmentDraftRequest(
    @Schema(nullable = true) @Valid @Nullable EnrollmentDraftData data,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @PositiveOrZero
        Long expectedRevision) {
  public @Nullable EnrollmentDraftData getData() {
    return data;
  }
}
