package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionPublicAccessUnavailableException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidPublicSubdomainException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionPublicAccessResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.validation.PublicSubdomainPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

@Service
@RequiredArgsConstructor
public class ResolveInstitutionPublicAccessUseCase {
  private final InstitutionRepository institutions;

  public InstitutionPublicAccessResponse execute(final String name) {
    try {
      PublicSubdomainPolicy.validate(name);
    } catch (InvalidPublicSubdomainException exception) {
      throw new InstitutionNotFoundException();
    }
    try {
      return InstitutionPublicAccessResponse.from(
          institutions
              .findByPublicSubdomainAndActiveTrue(name)
              .orElseThrow(InstitutionNotFoundException::new));
    } catch (DataAccessException | CannotCreateTransactionException exception) {
      throw new InstitutionPublicAccessUnavailableException();
    }
  }
}
