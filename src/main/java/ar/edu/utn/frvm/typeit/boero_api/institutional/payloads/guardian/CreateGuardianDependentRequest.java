package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.DOCUMENT_PATTERN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.MINIMUM_AGE;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_PATTERN;

import ar.edu.utn.frvm.typeit.boero_api.common.validation.MinimumAge;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.ValidationMessages;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateGuardianDependentRequest(
    @NotNull(message = ValidationMessages.DOCUMENT_REQUIRED)
        @Pattern(regexp = DOCUMENT_PATTERN, message = ValidationMessages.DOCUMENT_FORMAT)
        String documentNumber,
    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
        @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.FIRST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.FIRST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        String firstName,
    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
        @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.LAST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.LAST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        String lastName,
    @NotNull(message = ValidationMessages.BIRTH_DATE_REQUIRED)
        @Past(message = ValidationMessages.BIRTH_DATE_PAST)
        @MinimumAge(MINIMUM_AGE)
        LocalDate birthDate,
    @NotNull(message = ValidationMessages.GUARDIAN_RELATIONSHIP_REQUIRED)
        GuardianRelationship relationship,
    boolean isPrimaryContact) {}
