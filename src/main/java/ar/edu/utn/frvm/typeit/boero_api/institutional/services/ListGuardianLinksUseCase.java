package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  @Transactional(readOnly = true)
  public PaginatedResponse<GuardianLinkReviewResponse> page(
      final @Nullable UUID institutionId,
      final int page,
      final int size,
      final String search,
      final @Nullable GuardianLinkStatus status,
      final @Nullable Boolean hasDocumentation) {
    return PaginatedResponse.from(
        personGuardianRepository
            .findAll(
                GuardianLinkQuery.matching(institutionId, search, status, hasDocumentation),
                PageRequest.of(page, size, Sort.by("createdAt", "id")))
            .map(GuardianLinkReviewResponse::from));
  }

  /** Platform administrators may review requests across all institutions. */
  @Transactional(readOnly = true)
  public List<GuardianLinkReviewResponse> executeForPlatform(final GuardianLinkStatus status) {
    return personGuardianRepository.findByStatusOrderByCreatedAtAsc(status).stream()
        .map(GuardianLinkReviewResponse::from)
        .toList();
  }
}
