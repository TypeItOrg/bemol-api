package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import java.util.List;
import lombok.Builder;

@Builder
public record PlatformDashboardResponse(
    PlatformDashboardSummaryResponse summary,
    List<MonthlyInstitutionRegistrationResponse> institutionRegistrations,
    List<RecentInstitutionResponse> recentInstitutions) {}
