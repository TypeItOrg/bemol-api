package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"studyPlanId", "academicLevelIds", "includeUnassigned"})
public record EnrollmentPeriodOfferingRequest(
    @NotNull UUID studyPlanId,
    @NotNull List<@NotNull UUID> academicLevelIds,
    @NotNull Boolean includeUnassigned) {}
