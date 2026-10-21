package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.DOCUMENT_PATTERN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChangePendingEmailRequest(
    @NotNull(message = ValidationMessages.INSTITUTION_REQUIRED) UUID institutionId,
    @NotBlank(message = ValidationMessages.DOCUMENT_REQUIRED)
        @Pattern(regexp = DOCUMENT_PATTERN, message = ValidationMessages.DOCUMENT_FORMAT)
        String documentNumber,
    @NotBlank(message = ValidationMessages.PASSWORD_REQUIRED)
        @Size(max = PASSWORD_MAX, message = ValidationMessages.PASSWORD_MAX_LENGTH)
        String password,
    @NotBlank(message = ValidationMessages.PERSON_EMAIL_REQUIRED)
        @Email(message = ValidationMessages.PERSON_EMAIL_FORMAT)
        @Size(max = 150, message = ValidationMessages.PERSON_EMAIL_MAX_LENGTH)
        String email) {}
