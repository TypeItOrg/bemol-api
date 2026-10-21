package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CitySummaryResponse(UUID cityId, String name) {

  public static CitySummaryResponse from(City city) {
    return CitySummaryResponse.builder().cityId(city.getId()).name(city.getName()).build();
  }
}
