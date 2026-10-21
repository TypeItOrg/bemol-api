package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.CountrySummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.ProvinceListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListCountriesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListProvincesUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/countries")
@RequiredArgsConstructor
public class CountryController {

  private final ListCountriesUseCase listCountriesUseCase;
  private final ListProvincesUseCase listProvincesUseCase;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<CountrySummaryResponse> list(
      @RequestParam(required = false) String search,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listCountriesUseCase.execute(search, pageable);
  }

  @GetMapping(value = "/{countryId}/provinces", version = Version.V1)
  public PaginatedResponse<ProvinceListItemResponse> listProvincesByCountry(
      @PathVariable UUID countryId,
      @RequestParam(required = false) String search,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listProvincesUseCase.executeByCountry(countryId, search, pageable);
  }
}
