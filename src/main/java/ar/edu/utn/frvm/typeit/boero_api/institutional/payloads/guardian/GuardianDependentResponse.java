package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GuardianDependentResponse(
    UUID personGuardianId,
    UUID dependentPersonId,
    GuardianLinkStatus status,
    String documentNumber,
    @Nullable @Schema(nullable = true) String firstName,
    @Nullable @Schema(nullable = true) String lastName,
    @Nullable @Schema(nullable = true) LocalDate birthDate,
    GuardianRelationship relationship,
    boolean isPrimaryContact,
    long activeApplicationsCount,
    List<String> roles,
    Instant createdAt) {

  /**
   * Until the institution approves the link the tutor only gets back what they declared: the stored
   * name, roles and applications stay hidden, so requesting a link cannot be used to look up who is
   * registered under a document number.
   */
  public static GuardianDependentResponse from(
      final PersonGuardian link, final long activeApplicationsCount, final List<String> roles) {
    final Person dependent = link.getDependentPerson();
    final boolean visible = link.isActive();

    return new GuardianDependentResponse(
        link.getId(),
        dependent.getId(),
        link.getStatus(),
        dependent.getDocumentNumber(),
        visible ? dependent.getFirstName() : null,
        visible ? dependent.getLastName() : null,
        dependent.getBirthDate(),
        link.getRelationship(),
        link.isPrimaryContact(),
        visible ? activeApplicationsCount : 0,
        visible ? roles : List.of(),
        link.getCreatedAt());
  }
}
