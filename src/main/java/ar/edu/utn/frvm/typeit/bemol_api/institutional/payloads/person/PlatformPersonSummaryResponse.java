package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PlatformPersonSummaryResponse(
    UUID id,
    String firstName,
    String lastName,
    String documentNumber,
    String email,
    @Nullable String phoneNumber,
    UUID institutionId,
    String institutionName,
    List<PersonSummaryResponse.PersonRoleSummaryResponse> roles) {

  public static PlatformPersonSummaryResponse from(
      final Person person, final List<PersonSummaryResponse.PersonRoleSummaryResponse> roles) {
    return new PlatformPersonSummaryResponse(
        person.getId(),
        person.getFirstName(),
        person.getLastName(),
        person.getDocumentNumber(),
        person.getEmail(),
        person.getPhoneNumber(),
        person.getInstitution().getId(),
        person.getInstitution().getName(),
        roles);
  }
}
