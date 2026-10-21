package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementStage;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePrerequisiteRequest(
    @NotNull UUID requiredStudyPlanSpaceId,
    @NotNull RequirementStage requirementStage,
    @NotNull RequiredCondition requiredCondition) {}
