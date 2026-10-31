package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public record PlatformLoginChallengeResponse(String loginAttemptId, boolean setupRequired)
    implements PlatformLoginResponse {}
