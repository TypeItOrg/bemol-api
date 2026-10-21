package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.DOCUMENT_PATTERN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.MINIMUM_AGE;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints.NAME_PATTERN;

import ar.edu.utn.frvm.typeit.boero_api.common.validation.MinimumAge;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.ValidationMessages;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateGuardianDependentRequest(
    @NotNull(message = ValidationMessages.DOCUMENT_REQUIRED)
        @Pattern(regexp = DOCUMENT_PATTERN, message = ValidationMessages.DOCUMENT_FORMAT)
        String documentNumber,
    @org.jspecify.annotations.Nullable
        @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.FIRST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.FIRST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
        String firstName,
    @org.jspecify.annotations.Nullable
        @Size.List({
          @Size(min = NAME_MIN, message = ValidationMessages.LAST_NAME_MIN_LENGTH),
          @Size(max = NAME_MAX, message = ValidationMessages.LAST_NAME_MAX_LENGTH)
        })
        @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
        String lastName,
    @org.jspecify.annotations.Nullable
        @Past(message = ValidationMessages.BIRTH_DATE_PAST)
        @MinimumAge(MINIMUM_AGE)
        LocalDate birthDate,
    @NotNull(message = ValidationMessages.GUARDIAN_RELATIONSHIP_REQUIRED)
        GuardianRelationship relationship,
    boolean isPrimaryContact,
    @jakarta.validation.constraints.Email
        @jakarta.validation.constraints.Size(max = 150)
        @org.jspecify.annotations.Nullable
        String email,
    boolean ownAccessRequested,
    @org.jspecify.annotations.Nullable GuardianDependentRequestMode requestMode) {

  @AssertTrue(message = "Indicá nombre, apellido y fecha de nacimiento para registrar una persona.")
  public boolean isRegistrationDataPresent() {
    return requestMode == GuardianDependentRequestMode.EXISTING
        || (firstName != null
            && !firstName.isBlank()
            && lastName != null
            && !lastName.isBlank()
            && birthDate != null);
  }

  public CreateGuardianDependentRequest(
      String documentNumber,
      String firstName,
      String lastName,
      LocalDate birthDate,
      GuardianRelationship relationship,
      boolean isPrimaryContact,
      @org.jspecify.annotations.Nullable String email,
      boolean ownAccessRequested) {
    this(
        documentNumber,
        firstName,
        lastName,
        birthDate,
        relationship,
        isPrimaryContact,
        email,
        ownAccessRequested,
        null);
  }

  public CreateGuardianDependentRequest(
      String documentNumber,
      String firstName,
      String lastName,
      LocalDate birthDate,
      GuardianRelationship relationship,
      boolean isPrimaryContact) {
    this(
        documentNumber,
        firstName,
        lastName,
        birthDate,
        relationship,
        isPrimaryContact,
        null,
        false);
  }
}
