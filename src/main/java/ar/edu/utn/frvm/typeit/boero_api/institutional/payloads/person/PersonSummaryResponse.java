package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
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
      "enabled",
      "roles",
      "hasAccount",
      "accessInvitationPending"
    })
public record PersonSummaryResponse(
    UUID id,
    String firstName,
    String lastName,
    String documentNumber,
    @Schema(nullable = true) @Nullable String email,
    @Schema(nullable = true) @Nullable String phoneNumber,
    boolean enabled,
    List<PersonRoleSummaryResponse> roles,
    boolean hasAccount,
    boolean accessInvitationPending) {

  public record PersonRoleSummaryResponse(String roleCode, String displayName) {}

  public static PersonSummaryResponse from(final Person person) {
    return from(person, List.of(), true, true, false);
  }

  public static PersonSummaryResponse from(
      final Person person, final List<PersonRoleSummaryResponse> roles) {
    return from(person, roles, true, true, false);
  }

  public static PersonSummaryResponse from(
      final Person person,
      final List<PersonRoleSummaryResponse> roles,
      final boolean enabled,
      final boolean hasAccount,
      final boolean accessInvitationPending) {
    return new PersonSummaryResponse(
        person.getId(),
        person.getFirstName(),
        person.getLastName(),
        person.getDocumentNumber(),
        person.getEmail(),
        person.getPhoneNumber(),
        enabled,
        roles,
        hasAccount,
        accessInvitationPending);
  }
}
