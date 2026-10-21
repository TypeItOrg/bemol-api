package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"summary", "plans", "warnings"})
public record AcademicSpaceUsageResponse(
    AcademicSpaceUsageSummary summary,
    PaginatedResponse<AcademicSpaceUsagePlanResponse> plans,
    List<AcademicSpaceUsageWarning> warnings) {}
