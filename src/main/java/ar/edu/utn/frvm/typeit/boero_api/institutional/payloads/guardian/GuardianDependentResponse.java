package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GuardianDependentResponse(
    UUID personGuardianId,
    UUID dependentPersonId,
    String documentNumber,
    String firstName,
    String lastName,
    @Schema(nullable = true) LocalDate birthDate,
    GuardianRelationship relationship,
    boolean isPrimaryContact,
    long activeApplicationsCount,
    List<String> roles,
    Instant createdAt) {

  public static GuardianDependentResponse from(
      final PersonGuardian link, final long activeApplicationsCount, final List<String> roles) {
    final Person dependent = link.getDependentPerson();

    return new GuardianDependentResponse(
        link.getId(),
        dependent.getId(),
        dependent.getDocumentNumber(),
        dependent.getFirstName(),
        dependent.getLastName(),
        dependent.getBirthDate(),
        link.getRelationship(),
        link.isPrimaryContact(),
        activeApplicationsCount,
        roles,
        link.getCreatedAt());
  }
}
