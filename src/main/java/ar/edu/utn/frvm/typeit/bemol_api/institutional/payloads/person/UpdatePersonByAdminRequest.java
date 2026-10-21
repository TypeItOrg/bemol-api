package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_PATTERN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MIN;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdatePersonByAdminRequest(
    @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.FIRST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.FIRST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        @Nullable String firstName,
    @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.LAST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.LAST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        @Nullable String lastName,
    @Email(message = ValidationMessages.PERSON_EMAIL_FORMAT) @Nullable String email,
    @Nullable String phoneNumber,
    @Pattern(
            regexp = "^$|(?s:.{" + PASSWORD_MIN + "," + PASSWORD_MAX + "})$",
            message = ValidationMessages.PASSWORD_RANGE)
        @Nullable String password) {

  public UpdatePersonByAdminRequest(
      final @Nullable String firstName,
      final @Nullable String lastName,
      final @Nullable String email,
      final @Nullable String phoneNumber) {
    this(firstName, lastName, email, phoneNumber, null);
  }

  public boolean hasPassword() {
    return password != null && !password.isEmpty();
  }

  public boolean isEmpty() {
    return firstName == null
        && lastName == null
        && email == null
        && phoneNumber == null
        && !hasPassword();
  }
}
