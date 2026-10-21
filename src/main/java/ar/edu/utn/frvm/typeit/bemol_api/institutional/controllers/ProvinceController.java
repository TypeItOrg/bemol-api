package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.CityListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.ProvinceListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListCitiesUseCase;
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
@RequestMapping("/provinces")
@RequiredArgsConstructor
public class ProvinceController {

  private final ListProvincesUseCase listProvincesUseCase;
  private final ListCitiesUseCase listCitiesUseCase;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<ProvinceListItemResponse> list(
      @RequestParam(required = false) String search,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listProvincesUseCase.execute(search, pageable);
  }

  @GetMapping(value = "/{provinceId}/cities", version = Version.V1)
  public PaginatedResponse<CityListItemResponse> listCitiesByProvince(
      @PathVariable UUID provinceId,
      @RequestParam(required = false) String search,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listCitiesUseCase.executeByProvince(provinceId, search, pageable);
  }
}
