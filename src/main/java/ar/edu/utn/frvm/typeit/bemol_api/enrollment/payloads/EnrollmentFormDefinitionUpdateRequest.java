package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(requiredProperties = {"definition", "expectedRevision"})
public record EnrollmentFormDefinitionUpdateRequest(
    @NotNull @Valid EnrollmentFormDefinition definition,
    @NotNull @PositiveOrZero Long expectedRevision) {}
