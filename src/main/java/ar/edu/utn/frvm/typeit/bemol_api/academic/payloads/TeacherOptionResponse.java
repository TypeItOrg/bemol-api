package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "fullName"})
public record TeacherOptionResponse(UUID id, String fullName) {

  public static TeacherOptionResponse from(final Person person) {
    return new TeacherOptionResponse(
        person.getId(), person.getFirstName() + " " + person.getLastName());
  }
}
