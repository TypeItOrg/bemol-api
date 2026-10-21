package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CourseEnrollmentScheduleOptionResponse(
    UUID id,
    LocalTime startTime,
    LocalTime endTime,
    List<CourseIndividualSlotOptionResponse> individualSlots) {

  public static CourseEnrollmentScheduleOptionResponse from(
      final CourseClassSchedule schedule,
      final List<CourseIndividualSlotOptionResponse> individualSlots) {
    return new CourseEnrollmentScheduleOptionResponse(
        schedule.getId(), schedule.getStartTime(), schedule.getEndTime(), individualSlots);
  }
}
