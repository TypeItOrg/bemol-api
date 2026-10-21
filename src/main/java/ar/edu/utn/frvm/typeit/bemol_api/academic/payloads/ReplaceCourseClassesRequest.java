package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceCourseClassesRequest(
    @NotEmpty @Valid List<@NotNull @Valid CourseClassRequest> classes) {}
