package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetInstitutionUseCase {

  private final InstitutionRepository institutionRepository;

  public InstitutionDetailResponse execute(UUID id) {
    var institution =
        institutionRepository
            .findWithLocationById(id)
            .orElseThrow(InstitutionNotFoundException::new);

    return InstitutionDetailResponse.from(institution);
  }
}
