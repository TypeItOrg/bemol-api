package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "firstName",
      "lastName",
      "documentNumber",
      "email",
      "phoneNumber",
      "institutionId",
      "institutionName",
      "roles",
      "enabled",
      "hasAccount",
      "accessInvitationPending"
    })
public record PlatformPersonSummaryResponse(
    UUID id,
    String firstName,
    String lastName,
    String documentNumber,
    @Schema(nullable = true) @Nullable String email,
    @Schema(nullable = true) @Nullable String phoneNumber,
    UUID institutionId,
    String institutionName,
    List<PersonSummaryResponse.PersonRoleSummaryResponse> roles,
    boolean enabled,
    boolean hasAccount,
    boolean accessInvitationPending) {

  public static PlatformPersonSummaryResponse from(
      final Person person,
      final List<PersonSummaryResponse.PersonRoleSummaryResponse> roles,
      boolean enabled,
      boolean hasAccount,
      boolean accessInvitationPending) {
    return new PlatformPersonSummaryResponse(
        person.getId(),
        person.getFirstName(),
        person.getLastName(),
        person.getDocumentNumber(),
        person.getEmail(),
        person.getPhoneNumber(),
        person.getInstitution().getId(),
        person.getInstitution().getName(),
        roles,
        enabled,
        hasAccount,
        accessInvitationPending);
  }
}
