package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseIndividualSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.UUID;

@Schema(requiredProperties = {"id", "startTime", "endTime", "available"})
public record CourseIndividualSlotOptionResponse(
    UUID id, LocalTime startTime, LocalTime endTime, boolean available) {

  public static CourseIndividualSlotOptionResponse from(
      final CourseIndividualSlot slot, final boolean available) {
    return new CourseIndividualSlotOptionResponse(
        slot.getId(), slot.getStartTime(), slot.getEndTime(), available);
  }
}
