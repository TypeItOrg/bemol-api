package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CourseScheduleAssignmentRequest(
    @NotNull UUID classScheduleId, @Nullable UUID individualSlotId) {}
