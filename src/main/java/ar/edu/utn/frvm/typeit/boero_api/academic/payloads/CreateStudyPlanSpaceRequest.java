package ar.edu.utn.frvm.typeit.boero_api.academic.payloads;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateStudyPlanSpaceRequest(
    @NotNull UUID academicSpaceId,
    @Nullable UUID academicLevelId,
    @NotNull RequirementType requirementType,
    @Min(value = 1, message = ValidationMessages.ORDER_POSITIVE) int displayOrder,
    @NotNull ApprovalMode approvalMode,
    List<UUID> instrumentIds) {}
