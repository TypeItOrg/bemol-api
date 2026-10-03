package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListGuardianLinksUseCase {

  private final PersonGuardianRepository personGuardianRepository;

  /** Only the institution's own requests: the status filter never crosses tenants. */
  @Transactional(readOnly = true)
  public List<GuardianLinkReviewResponse> execute(
      final UUID institutionId, final GuardianLinkStatus status) {
    return personGuardianRepository
        .findByInstitution_IdAndStatusOrderByCreatedAtAsc(institutionId, status)
        .stream()
        .map(GuardianLinkReviewResponse::from)
        .toList();
  }
}
