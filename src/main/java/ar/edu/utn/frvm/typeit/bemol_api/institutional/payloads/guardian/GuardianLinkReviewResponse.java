package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A guardianship request as the institution sees it: who asks, for whom and with which tie. */
@Schema(
    requiredProperties = {
      "personGuardianId",
      "status",
      "relationship",
      "institution",
      "tutor",
      "dependent",
      "createdAt",
      "resolvedAt",
      "declaredFirstName",
      "declaredLastName",
      "declaredBirthDate",
      "matchingPerson"
    })
public record GuardianLinkReviewResponse(
    UUID personGuardianId,
    GuardianLinkStatus status,
    GuardianRelationship relationship,
    InstitutionSummary institution,
    PersonSummary tutor,
    PersonSummary dependent,
    Instant createdAt,
    @Nullable @Schema(nullable = true) Instant resolvedAt,
    @Nullable @Schema(nullable = true) String declaredFirstName,
    @Nullable @Schema(nullable = true) String declaredLastName,
    @Nullable @Schema(nullable = true) LocalDate declaredBirthDate,
    @Nullable @Schema(nullable = true) PersonSummary matchingPerson) {

  @Schema(requiredProperties = {"personId", "documentNumber", "firstName", "lastName", "birthDate"})
  public record PersonSummary(
      @Nullable @Schema(nullable = true) UUID personId,
      String documentNumber,
      @Nullable @Schema(nullable = true) String firstName,
      @Nullable @Schema(nullable = true) String lastName,
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

  @Schema(requiredProperties = {"institutionId", "name"})
  public record InstitutionSummary(UUID institutionId, String name) {
    static InstitutionSummary from(
        final ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution institution) {
      return new InstitutionSummary(institution.getId(), institution.getName());
    }
  }

  public static GuardianLinkReviewResponse from(final PersonGuardian link) {
    return from(link, null);
  }

  public static GuardianLinkReviewResponse from(
      final PersonGuardian link, final @Nullable Person matchingPerson) {
    return new GuardianLinkReviewResponse(
        link.getId(),
        link.getStatus(),
        link.getRelationship(),
        InstitutionSummary.from(link.getInstitution()),
        PersonSummary.from(link.getTutorPerson()),
        link.getDependentPersonId() != null
            ? PersonSummary.from(link.getDependentPerson())
            : new PersonSummary(
                null,
                link.getDocumentNumber(),
                link.getDeclaredFirstName(),
                link.getDeclaredLastName(),
                link.getDeclaredBirthDate()),
        link.getCreatedAt(),
        link.getResolvedAt(),
        link.getDeclaredFirstName(),
        link.getDeclaredLastName(),
        link.getDeclaredBirthDate(),
        matchingPerson == null ? null : PersonSummary.from(matchingPerson));
  }
}
