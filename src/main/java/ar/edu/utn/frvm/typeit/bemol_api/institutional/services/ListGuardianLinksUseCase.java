package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
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
  private final ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository people;

  private java.util.Map<String, ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person>
      candidates(
          final List<ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian>
              links) {
    if (links.isEmpty()) {
      return java.util.Map.of();
    }
    return people
        .findByDocumentNumberInAndInstitution_IdInAndDeletedFalse(
            links.stream().map(link -> link.getDocumentNumber()).distinct().toList(),
            links.stream().map(link -> link.getInstitution().getId()).distinct().toList())
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                person -> person.getInstitution().getId() + "|" + person.getDocumentNumber(),
                person -> person));
  }

  private List<GuardianLinkReviewResponse> responses(
      final List<ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian> links) {
    final var matches = candidates(links);
    return links.stream()
        .map(
            link ->
                GuardianLinkReviewResponse.from(
                    link,
                    matches.get(link.getInstitution().getId() + "|" + link.getDocumentNumber())))
        .toList();
  }

  /** Only the institution's own requests: the status filter never crosses tenants. */
  @Transactional(readOnly = true)
  public List<GuardianLinkReviewResponse> execute(
      final UUID institutionId, final GuardianLinkStatus status) {
    return responses(
        personGuardianRepository.findByInstitution_IdAndStatusOrderByCreatedAtAsc(
            institutionId, status));
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<GuardianLinkReviewResponse> page(
      final @Nullable UUID institutionId,
      final int page,
      final int size,
      final String search,
      final @Nullable GuardianLinkStatus status,
      final @Nullable Boolean hasDocumentation) {
    final var result =
        personGuardianRepository.findAll(
            GuardianLinkQuery.matching(institutionId, search, status, hasDocumentation),
            PageRequest.of(page, size, Sort.by("createdAt", "id")));
    final var matches = candidates(result.getContent());
    return PaginatedResponse.from(
        result.map(
            link ->
                GuardianLinkReviewResponse.from(
                    link,
                    matches.get(link.getInstitution().getId() + "|" + link.getDocumentNumber()))));
  }

  /** Platform administrators may review requests across all institutions. */
  @Transactional(readOnly = true)
  public List<GuardianLinkReviewResponse> executeForPlatform(final GuardianLinkStatus status) {
    return responses(personGuardianRepository.findByStatusOrderByCreatedAtAsc(status));
  }
}
