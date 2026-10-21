package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PersonNotFoundInInstitutionException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstitutionPersonResolver {

  private final PersonRepository personRepository;

  public Person requirePersonInInstitution(UUID institutionId, UUID personId) {
    return personRepository
        .findByIdAndInstitution_Id(personId, institutionId)
        .orElseThrow(PersonNotFoundInInstitutionException::new);
  }

  public Person requirePersonInInstitutionForUpdate(UUID institutionId, UUID personId) {
    return personRepository
        .findByIdAndInstitutionIdForUpdate(personId, institutionId)
        .orElseThrow(PersonNotFoundInInstitutionException::new);
  }
}
