package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseWithdrawalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WithdrawCourseEnrollmentRequest(
    @NotNull CourseWithdrawalType type, @NotBlank String reason, Long expectedVersion) {}
