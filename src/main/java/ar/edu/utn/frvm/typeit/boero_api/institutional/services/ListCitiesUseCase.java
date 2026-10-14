package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.ProvinceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.ProvinceRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CityListItemResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCitiesUseCase {

  private final CityRepository cityRepository;
  private final ProvinceRepository provinceRepository;

  public PaginatedResponse<CityListItemResponse> execute(
      @Nullable String search, Pageable pageable) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    if (normalizedSearch != null) {
      return PaginatedResponse.from(
          cityRepository
              .searchByNameOrProvince(normalizedSearch, pageable)
              .map(CityListItemResponse::from));
    }

    return PaginatedResponse.from(cityRepository.findAll(pageable).map(CityListItemResponse::from));
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<CityListItemResponse> executeByProvince(
      UUID provinceId, @Nullable String search, Pageable pageable) {
    if (!provinceRepository.existsById(provinceId)) {
      throw new ProvinceNotFoundException();
    }

    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    if (normalizedSearch != null) {
      return PaginatedResponse.from(
          cityRepository
              .searchByProvinceAndName(provinceId, normalizedSearch, pageable)
              .map(CityListItemResponse::from));
    }

    return PaginatedResponse.from(
        cityRepository.findByProvinceId(provinceId, pageable).map(CityListItemResponse::from));
  }
}
