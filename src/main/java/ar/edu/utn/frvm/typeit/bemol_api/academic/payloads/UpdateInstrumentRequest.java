package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.InstrumentIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateInstrumentRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 1000) @Nullable String description,
    @NotNull InstrumentIcon icon) {}
