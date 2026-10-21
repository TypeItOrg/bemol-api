package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateAcademicEnrollmentStatusRequest(
    @NotNull AcademicEnrollmentStatus status, @NotBlank String reason, Long expectedVersion) {}
