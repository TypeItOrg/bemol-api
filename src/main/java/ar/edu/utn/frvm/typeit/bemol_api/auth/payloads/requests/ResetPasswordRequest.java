package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MIN;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = ValidationMessages.PASSWORD_RECOVERY_TOKEN_REQUIRED) String token,
    @NotBlank(message = ValidationMessages.PASSWORD_REQUIRED)
        @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = ValidationMessages.PASSWORD_RANGE)
        String password,
    @NotBlank(message = ValidationMessages.CONFIRMATION_PASSWORD_REQUIRED)
        @Size(
            min = PASSWORD_MIN,
            max = PASSWORD_MAX,
            message = ValidationMessages.CONFIRMATION_PASSWORD_RANGE)
        String confirmPassword) {}
