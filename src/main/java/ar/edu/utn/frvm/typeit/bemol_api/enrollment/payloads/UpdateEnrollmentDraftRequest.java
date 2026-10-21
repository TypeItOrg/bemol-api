package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record UpdateEnrollmentDraftRequest(
    @Schema(nullable = true) @Valid @Nullable EnrollmentDraftData data) {
  public UpdateEnrollmentDraftRequest() {
    this(null);
  }

  public @Nullable EnrollmentDraftData getData() {
    return data;
  }
}
