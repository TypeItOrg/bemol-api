package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DiscoverablePasskeyOptionsRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = ValidationMessages.INSTITUTION_REQUIRED)
        UUID institutionId) {}
