package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "classNumber", "teachers", "days"})
public record CourseClassResponse(
    UUID id,
    int classNumber,
    List<CourseTeacherResponse> teachers,
    List<CourseClassDayResponse> days) {

  public static CourseClassResponse from(
      final CourseClass courseClass,
      final List<CourseTeacherResponse> teachers,
      final List<CourseClassDayResponse> days) {
    return new CourseClassResponse(
        courseClass.getId(), courseClass.getClassNumber(), teachers, days);
  }
}
