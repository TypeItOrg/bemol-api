package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEnrollmentPeriodRequest(
    @NotNull(message = EnrollmentMessages.ACADEMIC_YEAR_REQUIRED) UUID academicYearId,
    @NotBlank(message = EnrollmentMessages.NAME_REQUIRED)
        @Size(max = 150, message = EnrollmentMessages.NAME_TOO_LONG)
        String name,
    @NotNull(message = EnrollmentMessages.START_DATE_REQUIRED) Instant startDate,
    @NotNull(message = EnrollmentMessages.END_DATE_REQUIRED) Instant endDate,
    @NotEmpty @Valid List<EnrollmentPeriodOfferingRequest> offerings) {
  public CreateEnrollmentPeriodRequest(
      UUID academicYearId, String name, Instant startDate, Instant endDate) {
    this(academicYearId, name, startDate, endDate, List.of());
  }
}
