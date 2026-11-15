package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public record PlatformAccountSetupCodeResponse(String code, Instant expiresAt, boolean recovery) {}
