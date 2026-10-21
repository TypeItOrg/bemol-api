package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UpdateAddressRequest(
    @NotNull(message = ValidationMessages.CITY_REQUIRED) UUID cityId,
    @NotBlank(message = ValidationMessages.STREET_REQUIRED) String street,
    @Size(max = 50, message = ValidationMessages.NUMBER_MAX_LENGTH) @Nullable String number,
    @Size(max = 50, message = ValidationMessages.FLOOR_MAX_LENGTH) @Nullable String floor,
    @Size(max = 50, message = ValidationMessages.APARTMENT_MAX_LENGTH) @Nullable String apartment,
    @Nullable String neighborhood,
    @Nullable String additionalInfo) {}
