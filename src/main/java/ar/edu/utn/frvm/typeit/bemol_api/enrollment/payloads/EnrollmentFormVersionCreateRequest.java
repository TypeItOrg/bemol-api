package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(requiredProperties = {"sourceVersionId"})
public record EnrollmentFormVersionCreateRequest(@NotNull UUID sourceVersionId) {}
