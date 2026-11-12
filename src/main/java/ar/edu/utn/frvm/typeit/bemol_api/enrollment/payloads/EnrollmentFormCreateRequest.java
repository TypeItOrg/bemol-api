package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"name"})
public record EnrollmentFormCreateRequest(@NotBlank @Size(max = 150) String name) {}
