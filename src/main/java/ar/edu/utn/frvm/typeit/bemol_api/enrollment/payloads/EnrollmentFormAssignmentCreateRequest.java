package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(requiredProperties = {"trainingPathId", "academicYearId", "formVersionId"})
public record EnrollmentFormAssignmentCreateRequest(
    @NotNull UUID trainingPathId, @NotNull UUID academicYearId, @NotNull UUID formVersionId) {}
