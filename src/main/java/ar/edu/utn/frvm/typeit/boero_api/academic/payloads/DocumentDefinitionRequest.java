package ar.edu.utn.frvm.typeit.boero_api.academic.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DocumentDefinitionRequest(
    @NotBlank @Size(max = 150) String name,
    @NotNull @Size(max = 1000) String instructions,
    @NotEmpty
        List<@NotBlank @Pattern(regexp = "application/pdf|image/jpeg|image/png") String>
            allowedFormats,
    @NotNull Boolean active,
    @PositiveOrZero @Nullable Long revision,
    @Valid
        @Nullable List<@jakarta.validation.constraints.NotNull @Valid DocumentAssignmentRequest>
            assignments,
    @Nullable UUID targetInstitutionId) {}
