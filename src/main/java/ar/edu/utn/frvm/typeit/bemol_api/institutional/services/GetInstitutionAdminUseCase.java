package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionAdminDetailResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetInstitutionAdminUseCase {

  private final InstitutionRepository institutionRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public InstitutionAdminDetailResponse execute(UUID id) {
    var institution =
        institutionRepository
            .findWithLocationById(id)
            .orElseThrow(InstitutionNotFoundException::new);
    var userCount =
        userRepository.countEnabledUsersByInstitutionIdIn(List.of(id)).stream()
            .findFirst()
            .map(count -> count.getUserCount())
            .orElse(0L);

    return InstitutionAdminDetailResponse.from(institution, userCount);
  }
}
