package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPersonUseCase {

  private final PersonRepository personRepository;

  public PersonResponse execute(JwtAuthenticatedUser principal) {
    return personRepository
        .findWithDetailsByIdAndInstitution_Id(principal.personId(), principal.institutionId())
        .map(PersonResponse::from)
        .orElseThrow(PersonNotFoundException::new);
  }
}
