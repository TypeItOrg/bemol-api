package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_PATTERN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MIN;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdatePlatformAccountRequest(
    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
        @Size(min = NAME_MIN, max = NAME_MAX, message = ValidationMessages.FIRST_NAME_RANGE)
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        String name,
    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
        @Size(min = NAME_MIN, max = NAME_MAX, message = ValidationMessages.LAST_NAME_RANGE)
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        String lastName,
    @NotBlank(message = ValidationMessages.EMAIL_REQUIRED)
        @Email(message = ValidationMessages.EMAIL_FORMAT)
        @Size(max = 150, message = ValidationMessages.EMAIL_MAX_LENGTH)
        String email,
    @Pattern(
            regexp = "^$|(?s:.{" + PASSWORD_MIN + "," + PASSWORD_MAX + "})$",
            message = ValidationMessages.PASSWORD_RANGE)
        @Nullable String password) {}
