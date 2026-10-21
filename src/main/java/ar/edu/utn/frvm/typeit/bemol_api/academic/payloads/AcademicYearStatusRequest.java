package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import jakarta.validation.constraints.NotNull;

public record AcademicYearStatusRequest(@NotNull AcademicYearStatus status) {}
