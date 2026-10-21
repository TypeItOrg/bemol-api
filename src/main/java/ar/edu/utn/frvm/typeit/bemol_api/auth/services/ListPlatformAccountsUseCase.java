package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountAdminResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPlatformAccountsUseCase {

  private final PlatformAccountRepository platformAccountRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<PlatformAccountAdminResponse> execute(
      final String search, final Boolean enabled, final Pageable pageable) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    final var page =
        platformAccountRepository
            .findByFilters(normalizedSearch, enabled, pageable)
            .map(PlatformAccountAdminResponse::from);

    return PaginatedResponse.from(page);
  }
}
