package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "institutionName",
      "year",
      "startDate",
      "endDate",
      "status",
      "deletedAt"
    })
public record AcademicYearResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    int year,
    @Schema(nullable = true) @Nullable LocalDate startDate,
    @Schema(nullable = true) @Nullable LocalDate endDate,
    AcademicYearStatus status,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static AcademicYearResponse from(final AcademicYear year) {
    return new AcademicYearResponse(
        year.getId(),
        year.getInstitution().getId(),
        year.getInstitution().getName(),
        year.getYear(),
        year.getStartDate(),
        year.getEndDate(),
        year.getStatus(),
        year.getDeletedAt());
  }
}
