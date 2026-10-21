package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = ValidationMessages.REFRESH_TOKEN_REQUIRED) String refreshToken) {}
