package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.constraints.NotNull;

public record ActiveStatusRequest(@NotNull Boolean active) {}
