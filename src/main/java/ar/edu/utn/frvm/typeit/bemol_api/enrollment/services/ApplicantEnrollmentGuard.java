package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicantEnrollmentGuard {

  private final PersonRepository personRepository;
  private final OperatingContextResolver operatingContext;
  private final InstitutionalCapabilitiesService capabilities;

  public Person requireApplicant(final JwtAuthenticatedUser principal) {
    final UUID personId = selectedPersonId(principal);
    capabilities.requireOwnEnrollmentManage(principal.institutionId(), personId);

    return personRepository
        .findByIdAndInstitution_Id(personId, principal.institutionId())
        .orElseThrow(PersonNotFoundException::new);
  }

  public UUID selectedPersonId(final JwtAuthenticatedUser principal) {
    return operatingContext.currentPersonId().orElse(principal.personId());
  }
}
