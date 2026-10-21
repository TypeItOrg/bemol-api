package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateManualCourseEnrollmentRequest(
    @NotNull UUID studentId,
    @NotNull UUID courseId,
    @NotNull UUID courseClassId,
    @NotEmpty @Valid List<@NotNull @Valid CourseScheduleAssignmentRequest> assignments,
    Long expectedVersion,
    String idempotencyKey) {}
