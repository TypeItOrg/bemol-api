package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A guardianship request as the institution sees it: who asks, for whom and with which tie. */
public record GuardianLinkReviewResponse(
    UUID personGuardianId,
    GuardianLinkStatus status,
    GuardianRelationship relationship,
    InstitutionSummary institution,
    PersonSummary tutor,
    PersonSummary dependent,
    Instant createdAt,
    @Nullable @Schema(nullable = true) Instant resolvedAt) {

  public record PersonSummary(
      UUID personId,
      String documentNumber,
      String firstName,
      String lastName,
      @Nullable @Schema(nullable = true) LocalDate birthDate) {

    static PersonSummary from(final Person person) {
      return new PersonSummary(
          person.getId(),
          person.getDocumentNumber(),
          person.getFirstName(),
          person.getLastName(),
          person.getBirthDate());
    }
  }

  public record InstitutionSummary(UUID institutionId, String name) {
    static InstitutionSummary from(
        final ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution institution) {
      return new InstitutionSummary(institution.getId(), institution.getName());
    }
  }

  public static GuardianLinkReviewResponse from(final PersonGuardian link) {
    return new GuardianLinkReviewResponse(
        link.getId(),
        link.getStatus(),
        link.getRelationship(),
        InstitutionSummary.from(link.getInstitution()),
        PersonSummary.from(link.getTutorPerson()),
        PersonSummary.from(link.getDependentPerson()),
        link.getCreatedAt(),
        link.getResolvedAt());
  }
}
