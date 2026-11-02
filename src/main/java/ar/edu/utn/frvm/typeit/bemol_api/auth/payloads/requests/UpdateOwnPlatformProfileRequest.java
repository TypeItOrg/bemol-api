package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_PATTERN;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOwnPlatformProfileRequest(
    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
        @Size(min = NAME_MIN, max = NAME_MAX, message = ValidationMessages.FIRST_NAME_RANGE)
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        String name,
    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
        @Size(min = NAME_MIN, max = NAME_MAX, message = ValidationMessages.LAST_NAME_RANGE)
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        String lastName) {}
