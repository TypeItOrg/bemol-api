package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CountryNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CountryRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.ProvinceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.ProvinceListItemResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListProvincesUseCase {

  private final ProvinceRepository provinceRepository;
  private final CountryRepository countryRepository;

  public PaginatedResponse<ProvinceListItemResponse> execute(
      @Nullable String search, Pageable pageable) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    if (normalizedSearch != null) {
      return PaginatedResponse.from(
          provinceRepository
              .searchByName(normalizedSearch, pageable)
              .map(ProvinceListItemResponse::from));
    }
    return PaginatedResponse.from(
        provinceRepository.findAll(pageable).map(ProvinceListItemResponse::from));
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<ProvinceListItemResponse> executeByCountry(
      UUID countryId, @Nullable String search, Pageable pageable) {
    if (!countryRepository.existsById(countryId)) {
      throw new CountryNotFoundException();
    }

    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    if (normalizedSearch != null) {
      return PaginatedResponse.from(
          provinceRepository
              .searchByCountryIdAndName(countryId, normalizedSearch, pageable)
              .map(ProvinceListItemResponse::from));
    }

    return PaginatedResponse.from(
        provinceRepository
            .findByCountryId(countryId, pageable)
            .map(ProvinceListItemResponse::from));
  }
}
