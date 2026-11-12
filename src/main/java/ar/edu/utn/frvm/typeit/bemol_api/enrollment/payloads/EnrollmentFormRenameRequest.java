package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"name", "expectedRevision"})
public record EnrollmentFormRenameRequest(
    @NotBlank @Size(max = 150) String name, @NotNull @PositiveOrZero Long expectedRevision) {}
