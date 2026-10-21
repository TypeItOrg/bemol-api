package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.MINIMUM_AGE;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_PATTERN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MIN;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.MinimumAge;
import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UpdatePersonRequest(
    @Size(min = NAME_MIN, message = ValidationMessages.FIRST_NAME_MIN_LENGTH)
        @Size(max = NAME_MAX, message = ValidationMessages.FIRST_NAME_MAX_LENGTH)
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        @Nullable String firstName,
    @Size(min = NAME_MIN, message = ValidationMessages.LAST_NAME_MIN_LENGTH)
        @Size(max = NAME_MAX, message = ValidationMessages.LAST_NAME_MAX_LENGTH)
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        @Nullable String lastName,
    @MinimumAge(MINIMUM_AGE) @Nullable LocalDate birthDate,
    @Email(message = ValidationMessages.EMAIL_FORMAT)
        @Size(max = 150, message = ValidationMessages.EMAIL_MAX_LENGTH)
        @Nullable String email,
    @Size(max = 30, message = ValidationMessages.PHONE_MAX_LENGTH) @Nullable String phoneNumber,
    @Nullable UUID birthCityId,
    @Nullable UUID nationalityCountryId,
    @Valid @Nullable UpdateAddressRequest address,
    @Size(max = PASSWORD_MAX, message = ValidationMessages.CURRENT_PASSWORD_MAX_LENGTH)
        @Nullable String currentPassword,
    @Pattern(
            regexp = "^$|(?s:.{" + PASSWORD_MIN + "," + PASSWORD_MAX + "})$",
            message = ValidationMessages.PASSWORD_RANGE)
        @Nullable String password) {

  public UpdatePersonRequest(
      @Nullable String firstName,
      @Nullable String lastName,
      @Nullable LocalDate birthDate,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable UUID birthCityId,
      @Nullable UUID nationalityCountryId,
      @Nullable UpdateAddressRequest address) {
    this(
        firstName,
        lastName,
        birthDate,
        email,
        phoneNumber,
        birthCityId,
        nationalityCountryId,
        address,
        null,
        null);
  }

  public UpdatePersonRequest(
      @Nullable String firstName,
      @Nullable String lastName,
      @Nullable LocalDate birthDate,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable UUID birthCityId,
      @Nullable UUID nationalityCountryId,
      @Nullable UpdateAddressRequest address,
      @Nullable String password) {
    this(
        firstName,
        lastName,
        birthDate,
        email,
        phoneNumber,
        birthCityId,
        nationalityCountryId,
        address,
        null,
        password);
  }
}
