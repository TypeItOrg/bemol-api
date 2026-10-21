package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record RejectEnrollmentApplicationCourseRequest(
    @NotBlank @Size(max = 1000) String reason, @Nullable Long expectedVersion) {}
