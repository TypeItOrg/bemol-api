package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CourseClassRequest(
    @NotEmpty List<@NotNull UUID> teacherIds,
    @NotEmpty @Valid List<@NotNull @Valid CourseClassDayRequest> days) {}
