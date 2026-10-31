package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyListResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPasskeysUseCase {

  private final PasskeyCredentialRepository passkeyCredentialRepository;

  @Transactional(readOnly = true)
  public List<PasskeyResponse> execute(final JwtAuthenticatedUser principal) {
    ensurePersonalPrincipal(principal);

    return passkeyCredentialRepository.findActiveByUserId(principal.userId()).stream()
        .map(PasskeyResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PasskeyListResponse execute(
      final JwtAuthenticatedUser principal,
      final @Nullable String search,
      final @Nullable Boolean used,
      final @Nullable Sort sort,
      final int maxActivePasskeys) {
    ensurePersonalPrincipal(principal);

    final String normalizedSearch = SearchNormalization.normalizeSearch(search);

    final List<PasskeyResponse> items =
        passkeyCredentialRepository
            .findActiveByFilters(
                principal.userId(),
                normalizedSearch,
                used,
                PlatformPasskeyManagementUseCase.resolveSort(sort))
            .stream()
            .map(PasskeyResponse::from)
            .toList();

    final int activeCount =
        normalizedSearch == null && used == null
            ? items.size()
            : Math.toIntExact(passkeyCredentialRepository.countActiveByUserId(principal.userId()));

    return new PasskeyListResponse(items, maxActivePasskeys, activeCount);
  }

  private static void ensurePersonalPrincipal(final JwtAuthenticatedUser principal) {
    if (principal.delegated() || principal.accountUserId().isEmpty()) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException();
    }
  }
}
