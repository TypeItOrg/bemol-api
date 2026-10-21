package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateAcademicLevelRequest(
    @Min(value = 1, message = ValidationMessages.ORDER_POSITIVE) int displayOrder,
    @Size(max = 1000) @Nullable String description) {}
