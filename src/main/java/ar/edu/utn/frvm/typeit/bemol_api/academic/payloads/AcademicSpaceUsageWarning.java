package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceUsageWarningCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"code", "blockingPlanCount"})
public record AcademicSpaceUsageWarning(
    AcademicSpaceUsageWarningCode code, long blockingPlanCount) {}
