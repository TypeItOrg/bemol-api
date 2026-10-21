package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CourseClassDayRequest(
    @NotNull CourseDay dayOfWeek,
    @Positive @Nullable Integer capacity,
    @Positive @Nullable Integer periodDurationMinutes,
    @NotEmpty @Valid List<@NotNull @Valid CourseClassScheduleRequest> schedules) {}
