package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateInstrumentRequest(
    @NotBlank @Size(max = 150) String name, @Size(max = 1000) @Nullable String description) {}
