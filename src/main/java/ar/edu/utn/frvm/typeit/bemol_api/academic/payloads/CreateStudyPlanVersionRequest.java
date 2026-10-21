package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record CreateStudyPlanVersionRequest(
    @NotBlank @Size(max = 150) String name,
    @Nullable LocalDate effectiveFrom,
    @Nullable LocalDate effectiveTo) {}
