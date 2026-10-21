package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record DiscoverablePasskeyVerifyRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = ValidationMessages.CEREMONY_REQUIRED)
        String ceremonyId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull JsonNode credential,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean rememberMe) {}
