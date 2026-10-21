package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;

@Schema(requiredProperties = {"dayOfWeek", "capacity", "periodDurationMinutes", "schedules"})
public record CourseClassDayResponse(
    CourseDay dayOfWeek,
    @Nullable Integer capacity,
    @Nullable Integer periodDurationMinutes,
    List<CourseClassScheduleResponse> schedules) {

  public static CourseClassDayResponse from(
      final CourseClassDay day, final List<CourseClassScheduleResponse> schedules) {
    return new CourseClassDayResponse(
        day.getDayOfWeek(), day.getCapacity(), day.getPeriodDurationMinutes(), schedules);
  }
}
