package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.LastInstitutionalAuthorityException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstitutionalAuthorityContinuityGuard {
  private final PersonRoleAssignmentRepository assignments;

  public void requireOtherAuthorityBeforeDeletion(final Person person) {
    final var institutionId = person.getInstitution().getId();
    final boolean authority =
        assignments.findByPerson_IdAndInstitution_Id(person.getId(), institutionId).stream()
            .anyMatch(assignment -> assignment.getRole().isInstitutionalAuthority());
    if (authority && !assignments.existsCurrentAuthorityOtherThan(institutionId, person.getId())) {
      throw new LastInstitutionalAuthorityException();
    }
  }
}
