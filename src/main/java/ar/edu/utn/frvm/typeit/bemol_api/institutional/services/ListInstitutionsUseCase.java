package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionListItemResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListInstitutionsUseCase {

  private final InstitutionRepository institutionRepository;

  public PaginatedResponse<InstitutionListItemResponse> execute(
      final @Nullable String search, final @Nullable Boolean active, final Pageable pageable) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    return PaginatedResponse.from(
        institutionRepository
            .findWithLocationByFilters(normalizedSearch, active, pageable)
            .map(InstitutionListItemResponse::from));
  }
}
