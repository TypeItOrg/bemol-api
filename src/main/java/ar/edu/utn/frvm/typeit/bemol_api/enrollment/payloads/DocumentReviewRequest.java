package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentReviewRequest(
    @NotNull DocumentReviewStatus status, @Size(max = 2000) String observation) {}
