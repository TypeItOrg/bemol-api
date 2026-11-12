package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record UpdateEnrollmentDraftRequest(
    @Schema(nullable = true) @Valid @Nullable EnrollmentDraftData data,
    @Schema(
            nullable = true,
            description = "Obligatoria para formularios institucionales; opcional en BASE_V1.")
        @PositiveOrZero
        @Nullable Long expectedRevision) {
  public UpdateEnrollmentDraftRequest() {
    this(null, null);
  }

  public UpdateEnrollmentDraftRequest(final @Nullable EnrollmentDraftData data) {
    this(data, null);
  }

  public @Nullable EnrollmentDraftData getData() {
    return data;
  }
}
