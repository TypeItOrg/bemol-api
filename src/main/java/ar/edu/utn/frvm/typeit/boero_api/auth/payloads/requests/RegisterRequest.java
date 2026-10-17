package ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.DOCUMENT_PATTERN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.MINIMUM_AGE;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_PATTERN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.PASSWORD_MIN;

import ar.edu.utn.frvm.typeit.boero_api.common.validation.MinimumAge;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterRequest(
    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
        @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.FIRST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.FIRST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        String name,
    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
        @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.LAST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.LAST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        String lastName,
    @NotNull(message = ValidationMessages.BIRTH_DATE_REQUIRED) @MinimumAge(MINIMUM_AGE)
        LocalDate birthDate,
    @NotNull(message = ValidationMessages.DOCUMENT_REQUIRED)
        @Pattern(regexp = DOCUMENT_PATTERN, message = ValidationMessages.DOCUMENT_FORMAT)
        String documentNumber,
    @NotBlank(message = ValidationMessages.PERSON_EMAIL_REQUIRED)
        @Email(message = ValidationMessages.PERSON_EMAIL_FORMAT)
        @Size(max = 150, message = ValidationMessages.PERSON_EMAIL_MAX_LENGTH)
        String email,
    @NotNull(message = ValidationMessages.PASSWORD_REQUIRED)
        @Size.List({
          @Size(min = PASSWORD_MIN, message = ValidationMessages.PASSWORD_MIN_LENGTH),
          @Size(max = PASSWORD_MAX, message = ValidationMessages.PASSWORD_MAX_LENGTH)
        })
        String password,
    @NotNull(message = ValidationMessages.INSTITUTION_REQUIRED) UUID institutionId) {}
