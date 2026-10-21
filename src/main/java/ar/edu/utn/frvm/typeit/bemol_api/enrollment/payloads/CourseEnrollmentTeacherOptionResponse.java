package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.UUID;

public record CourseEnrollmentTeacherOptionResponse(UUID personId, String fullName) {

  public static CourseEnrollmentTeacherOptionResponse from(final Person person) {
    final var firstName = person.getFirstName() == null ? "" : person.getFirstName();
    final var lastName = person.getLastName() == null ? "" : person.getLastName();
    return new CourseEnrollmentTeacherOptionResponse(
        person.getId(), (firstName + " " + lastName).trim());
  }
}
