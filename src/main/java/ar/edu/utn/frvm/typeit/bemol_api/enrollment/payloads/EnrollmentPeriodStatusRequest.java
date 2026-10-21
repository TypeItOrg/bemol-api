package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import jakarta.validation.constraints.NotNull;

public record EnrollmentPeriodStatusRequest(
    @NotNull(message = EnrollmentMessages.STATUS_REQUIRED) EnrollmentPeriodStatus status) {}
