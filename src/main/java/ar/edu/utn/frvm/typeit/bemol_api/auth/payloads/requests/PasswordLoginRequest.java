package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordLoginRequest(
    @NotBlank(message = ValidationMessages.LOGIN_ATTEMPT_REQUIRED) String loginAttemptId,
    @NotBlank(message = ValidationMessages.PASSWORD_REQUIRED)
        @Size(max = PASSWORD_MAX, message = ValidationMessages.PASSWORD_MAX_LENGTH)
        String password,
    Boolean rememberMe) {}
