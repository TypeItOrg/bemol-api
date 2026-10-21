package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record EnrollApplicationCourseRequest(
    @NotNull UUID courseClassId,
    @NotEmpty @Valid List<@NotNull @Valid CourseScheduleAssignmentRequest> assignments,
    @Nullable Long expectedVersion) {}
