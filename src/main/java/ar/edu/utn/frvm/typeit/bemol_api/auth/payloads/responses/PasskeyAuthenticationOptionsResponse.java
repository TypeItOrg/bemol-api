package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public record PasskeyAuthenticationOptionsResponse(String ceremonyId, JsonNode options) {}
