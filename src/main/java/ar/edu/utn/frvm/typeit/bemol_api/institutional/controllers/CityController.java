package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.CityListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListCitiesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {

  private final ListCitiesUseCase listCitiesUseCase;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<CityListItemResponse> list(
      @RequestParam(required = false) String search,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listCitiesUseCase.execute(search, pageable);
  }
}
