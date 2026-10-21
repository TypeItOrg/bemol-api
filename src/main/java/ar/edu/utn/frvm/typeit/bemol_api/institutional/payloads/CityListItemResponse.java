package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CityListItemResponse(UUID id, String name, UUID provinceId, String province) {

  public static CityListItemResponse from(City city) {
    return CityListItemResponse.builder()
        .id(city.getId())
        .name(city.getName())
        .provinceId(city.getProvince().getId())
        .province(city.getProvince().getName())
        .build();
  }
}
