package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    requiredProperties = {
      "totalPlans",
      "activePlans",
      "draftPlans",
      "inactivePlans",
      "totalPlacements",
      "unassignedPlacements",
      "deactivationBlocked"
    })
public record AcademicSpaceUsageSummary(
    long totalPlans,
    long activePlans,
    long draftPlans,
    long inactivePlans,
    long totalPlacements,
    long unassignedPlacements,
    boolean deactivationBlocked) {}
