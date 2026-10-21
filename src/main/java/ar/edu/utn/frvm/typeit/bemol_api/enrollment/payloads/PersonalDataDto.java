package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(
    requiredProperties = {
      "firstName",
      "lastName",
      "documentNumber",
      "birthDate",
      "phoneNumber",
      "email"
    })
public record PersonalDataDto(
    @Schema(nullable = true) @Nullable String firstName,
    @Schema(nullable = true) @Nullable String lastName,
    @Schema(nullable = true) @Nullable String documentNumber,
    @Schema(nullable = true) @Nullable LocalDate birthDate,
    @Schema(nullable = true) @Nullable String phoneNumber,
    @Schema(nullable = true) @Nullable String email) {
  public PersonalDataDto() {
    this(null, null, null, null, null, null);
  }

  public @Nullable String getFirstName() {
    return firstName;
  }

  public @Nullable String getLastName() {
    return lastName;
  }

  public @Nullable String getDocumentNumber() {
    return documentNumber;
  }

  public @Nullable LocalDate getBirthDate() {
    return birthDate;
  }

  public @Nullable String getPhoneNumber() {
    return phoneNumber;
  }

  public @Nullable String getEmail() {
    return email;
  }
}
