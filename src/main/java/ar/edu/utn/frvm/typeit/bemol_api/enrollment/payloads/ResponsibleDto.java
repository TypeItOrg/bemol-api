package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(
    requiredProperties = {
      "fullName",
      "documentNumber",
      "occupation",
      "phoneNumber",
      "email",
      "educationLevel"
    })
public record ResponsibleDto(
    @Schema(nullable = true) @Nullable String fullName,
    @Schema(nullable = true) @Nullable String documentNumber,
    @Schema(nullable = true) @Nullable String occupation,
    @Schema(nullable = true) @Nullable String phoneNumber,
    @Schema(nullable = true) @Nullable String email,
    @Schema(nullable = true) @Nullable String educationLevel) {
  public ResponsibleDto() {
    this(null, null, null, null, null, null);
  }

  public @Nullable String getFullName() {
    return fullName;
  }

  public @Nullable String getDocumentNumber() {
    return documentNumber;
  }

  public @Nullable String getOccupation() {
    return occupation;
  }

  public @Nullable String getPhoneNumber() {
    return phoneNumber;
  }

  public @Nullable String getEmail() {
    return email;
  }

  public @Nullable String getEducationLevel() {
    return educationLevel;
  }
}
