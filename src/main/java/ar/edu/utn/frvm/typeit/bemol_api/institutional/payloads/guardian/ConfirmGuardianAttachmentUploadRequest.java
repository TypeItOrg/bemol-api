package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ConfirmGuardianAttachmentUploadRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String storageKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String fileName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String contentType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Positive long fileSize) {}
