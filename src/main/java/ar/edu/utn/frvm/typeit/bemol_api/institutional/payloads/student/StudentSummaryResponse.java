package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.student;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.StudentStatus;
import java.time.LocalDate;
import java.util.UUID;

public record StudentSummaryResponse(
    UUID studentId,
    UUID personId,
    String firstName,
    String lastName,
    String documentNumber,
    String fileNumber,
    StudentStatus status,
    LocalDate enrollmentDate) {

  public static StudentSummaryResponse from(final Student student) {
    final var person = student.getPerson();
    return new StudentSummaryResponse(
        student.getId(),
        person.getId(),
        person.getFirstName(),
        person.getLastName(),
        person.getDocumentNumber(),
        student.getFileNumber(),
        student.getStatus(),
        student.getEnrollmentDate());
  }
}
