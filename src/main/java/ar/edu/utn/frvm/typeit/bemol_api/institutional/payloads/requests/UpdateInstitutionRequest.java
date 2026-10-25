package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UpdateInstitutionRequest(
    @NotBlank(message = ValidationMessages.NAME_REQUIRED)
        @Size(max = 255, message = ValidationMessages.NAME_MAX_LENGTH)
        String name,
    @Size(max = 50, message = InstitutionMessages.SHORT_NAME_MAX_LENGTH) @Nullable String shortName,
    @NotNull(message = ValidationMessages.CITY_REQUIRED) UUID cityId,
    @Nullable String street,
    @Size(max = 50, message = ValidationMessages.NUMBER_MAX_LENGTH) @Nullable String number,
    @Nullable String neighborhood,
    @Nullable String additionalInfo,
    @Size(max = 30, message = ValidationMessages.PHONE_MAX_LENGTH) @Nullable String phoneNumber,
    @Email(message = ValidationMessages.EMAIL_FORMAT)
        @Size(max = 150, message = ValidationMessages.EMAIL_MAX_LENGTH)
        @Nullable String email,
    @NotNull(message = ValidationMessages.ACTIVE_REQUIRED) Boolean active) {}
