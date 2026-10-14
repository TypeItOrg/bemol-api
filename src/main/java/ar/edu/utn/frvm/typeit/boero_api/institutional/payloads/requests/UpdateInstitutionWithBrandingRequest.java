package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record UpdateInstitutionWithBrandingRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid
        UpdateInstitutionRequest institution,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @JsonProperty(value = "publicSubdomain", required = true)
        @Nullable String publicSubdomain,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LogoIntent logoIntent) {
  public enum LogoIntent {
    KEEP,
    REPLACE,
    REMOVE
  }
}
