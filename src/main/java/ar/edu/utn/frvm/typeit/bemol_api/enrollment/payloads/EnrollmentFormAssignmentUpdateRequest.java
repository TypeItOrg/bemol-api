package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

@Schema(requiredProperties = {"formVersionId", "expectedRevision"})
public record EnrollmentFormAssignmentUpdateRequest(
    @NotNull UUID formVersionId, @NotNull @PositiveOrZero Long expectedRevision) {}
