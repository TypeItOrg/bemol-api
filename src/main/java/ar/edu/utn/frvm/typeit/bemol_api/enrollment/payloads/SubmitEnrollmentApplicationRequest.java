package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.Nullable;

public record SubmitEnrollmentApplicationRequest(
    @Schema(
            nullable = true,
            description = "Obligatoria para formularios institucionales; opcional en BASE_V1.")
        @PositiveOrZero
        @Nullable Long expectedRevision) {}
