package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record CourseClassScheduleRequest(
    @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
