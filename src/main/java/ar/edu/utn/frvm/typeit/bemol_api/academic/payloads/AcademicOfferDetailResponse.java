package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"offer", "levels", "unassignedSpaces"})
public record AcademicOfferDetailResponse(
    AcademicOfferSummaryResponse offer,
    List<AcademicOfferLevelResponse> levels,
    List<AcademicOfferSpaceResponse> unassignedSpaces) {}
