package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record CreateAcademicYearRequest(
    @Min(value = 2000, message = ValidationMessages.YEAR_MINIMUM) int year,
    LocalDate startDate,
    LocalDate endDate) {}
