package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassTeacher;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"personId", "fullName"})
public record CourseTeacherResponse(UUID personId, String fullName) {

  public static CourseTeacherResponse from(final CourseClassTeacher teacher) {
    final var person = teacher.getPerson();
    return new CourseTeacherResponse(
        person.getId(), person.getFirstName() + " " + person.getLastName());
  }
}
