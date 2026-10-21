package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record StudyPlanStatusRequest(
    @NotNull StudyPlanStatus status, @Nullable LocalDate effectiveTo) {}
