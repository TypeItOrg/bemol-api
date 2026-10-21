package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CourseSelectionDto(@NotNull UUID courseId, @Nullable UUID preferredTeacherId) {}
