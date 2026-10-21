package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
@Builder
public record UserRegisteredResponse(
    UUID userId, String documentNumber, UUID institutionId, boolean emailVerificationRequired) {}
