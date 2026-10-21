package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record PasskeyRegistrationVerifyRequest(
    @NotBlank(message = ValidationMessages.CEREMONY_REQUIRED) String ceremonyId,
    @NotNull JsonNode credential) {}
