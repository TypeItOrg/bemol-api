package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(requiredProperties = {"expectedRevision"})
public record EnrollmentFormRevisionRequest(@NotNull @PositiveOrZero Long expectedRevision) {}
