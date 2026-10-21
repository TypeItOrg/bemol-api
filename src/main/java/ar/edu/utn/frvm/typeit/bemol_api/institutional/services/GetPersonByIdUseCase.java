package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPersonByIdUseCase {

  private final PersonRepository personRepository;

  @Transactional(readOnly = true)
  public PersonResponse execute(final UUID institutionId, final UUID personId) {
    return personRepository
        .findWithDetailsByIdAndInstitution_Id(personId, institutionId)
        .map(PersonResponse::from)
        .orElseThrow(PersonNotFoundException::new);
  }
}
