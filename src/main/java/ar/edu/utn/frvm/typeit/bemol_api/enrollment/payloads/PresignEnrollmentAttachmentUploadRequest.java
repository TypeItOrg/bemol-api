package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PresignEnrollmentAttachmentUploadRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID requirementId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String fileName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String contentType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Positive long fileSize) {}
