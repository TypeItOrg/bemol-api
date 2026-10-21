package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(requiredProperties = {"startTime", "endTime"})
public record CourseClassScheduleResponse(LocalTime startTime, LocalTime endTime) {

  public static CourseClassScheduleResponse from(final CourseClassSchedule schedule) {
    return new CourseClassScheduleResponse(schedule.getStartTime(), schedule.getEndTime());
  }
}
