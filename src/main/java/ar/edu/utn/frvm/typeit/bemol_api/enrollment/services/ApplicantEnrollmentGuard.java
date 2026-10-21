package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicantEnrollmentGuard {

  private final PersonRepository personRepository;
  private final OperatingContextResolver operatingContext;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;

  public Person requireApplicant(final JwtAuthenticatedUser principal) {
    final UUID personId = selectedPersonId(principal);
    final boolean canApply =
        hasRole(principal, personId, SystemRoleCode.APPLICANT)
            || hasRole(principal, personId, SystemRoleCode.STUDENT);

    if (!canApply) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }

    return personRepository
        .findByIdAndInstitution_Id(personId, principal.institutionId())
        .orElseThrow(PersonNotFoundException::new);
  }

  public UUID selectedPersonId(final JwtAuthenticatedUser principal) {
    return operatingContext.currentPersonId().orElse(principal.personId());
  }

  private boolean hasRole(
      final JwtAuthenticatedUser principal, final UUID personId, final SystemRoleCode role) {
    return personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
        personId, principal.institutionId(), role.name());
  }
}
