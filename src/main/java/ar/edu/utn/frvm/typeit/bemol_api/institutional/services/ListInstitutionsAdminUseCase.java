package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionUserCount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionAdminListItemResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListInstitutionsAdminUseCase {

  private final InstitutionRepository institutionRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<InstitutionAdminListItemResponse> execute(
      final @Nullable String search, final @Nullable Boolean active, final Pageable pageable) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    var page = institutionRepository.findWithLocationByFilters(normalizedSearch, active, pageable);
    var items = page.getContent();

    var counts =
        items.isEmpty()
            ? Map.<UUID, Long>of()
            : toMap(userRepository.countEnabledUsersByInstitutionIdIn(toIds(items)));

    var adminItems =
        items.stream()
            .map(
                institution ->
                    InstitutionAdminListItemResponse.from(
                        institution, counts.getOrDefault(institution.getId(), 0L)))
            .toList();

    return PaginatedResponse.<InstitutionAdminListItemResponse>builder()
        .items(adminItems)
        .page(page.getNumber())
        .size(page.getSize())
        .totalItems(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
  }

  private static Collection<UUID> toIds(List<Institution> institutions) {
    return institutions.stream().map(mappedInstitution -> mappedInstitution.getId()).toList();
  }

  private static Map<UUID, Long> toMap(List<InstitutionUserCount> rows) {
    return rows.stream()
        .collect(Collectors.toMap(row -> row.getInstitutionId(), row -> row.getUserCount()));
  }
}
