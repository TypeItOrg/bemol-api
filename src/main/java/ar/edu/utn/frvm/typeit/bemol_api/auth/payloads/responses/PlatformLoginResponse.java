package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(oneOf = {PlatformAuthResponse.class, PlatformLoginChallengeResponse.class})
public sealed interface PlatformLoginResponse
    permits PlatformAuthResponse, PlatformLoginChallengeResponse {}
