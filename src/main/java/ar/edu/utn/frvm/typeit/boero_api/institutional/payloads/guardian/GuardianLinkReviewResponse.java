package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A guardianship request as the institution sees it: who asks, for whom and with which tie. */
public record GuardianLinkReviewResponse(
    UUID personGuardianId,
    GuardianLinkStatus status,
    GuardianRelationship relationship,
    PersonSummary tutor,
    PersonSummary dependent,
    Instant createdAt,
    @Schema(nullable = true) Instant resolvedAt) {

  public record PersonSummary(
      UUID personId,
      String documentNumber,
      String firstName,
      String lastName,
      @Schema(nullable = true) LocalDate birthDate) {

    static PersonSummary from(final Person person) {
      return new PersonSummary(
          person.getId(),
          person.getDocumentNumber(),
          person.getFirstName(),
          person.getLastName(),
          person.getBirthDate());
    }
  }

  public static GuardianLinkReviewResponse from(final PersonGuardian link) {
    return new GuardianLinkReviewResponse(
        link.getId(),
        link.getStatus(),
        link.getRelationship(),
        PersonSummary.from(link.getTutorPerson()),
        PersonSummary.from(link.getDependentPerson()),
        link.getCreatedAt(),
        link.getResolvedAt());
  }
}
