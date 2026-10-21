package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record UpdateEnrollmentPeriodRequest(
    @NotBlank(message = EnrollmentMessages.NAME_REQUIRED)
        @Size(max = 150, message = EnrollmentMessages.NAME_TOO_LONG)
        String name,
    @NotNull(message = EnrollmentMessages.START_DATE_REQUIRED) Instant startDate,
    @NotNull(message = EnrollmentMessages.END_DATE_REQUIRED) Instant endDate,
    @NotEmpty @Valid List<EnrollmentPeriodOfferingRequest> offerings) {
  public UpdateEnrollmentPeriodRequest(String name, Instant startDate, Instant endDate) {
    this(name, startDate, endDate, List.of());
  }
}
