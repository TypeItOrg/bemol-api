package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignInstitutionLogoUploadRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String contentType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Positive long fileSize) {}
