package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_PATTERN;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record UpdatePersonByAdminRequest(
    @Size(min = NAME_MIN, max = NAME_MAX) @Pattern(regexp = NAME_PATTERN)
        @Nullable String firstName,
    @Size(min = NAME_MIN, max = NAME_MAX) @Pattern(regexp = NAME_PATTERN) @Nullable String lastName,
    @Pattern(
            regexp =
                ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints
                    .DOCUMENT_PATTERN)
        @Nullable String documentNumber,
    @ar.edu.utn.frvm.typeit.bemol_api.common.validation.MinimumAge(
            ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.MINIMUM_AGE)
        @Nullable LocalDate birthDate,
    @Size(max = 30) @Nullable String phoneNumber) {
  public boolean isEmpty() {
    return firstName == null
        && lastName == null
        && documentNumber == null
        && birthDate == null
        && phoneNumber == null;
  }
}
