package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProvinceListItemResponse(UUID id, String name) {

  public static ProvinceListItemResponse from(Province province) {
    return ProvinceListItemResponse.builder().id(province.getId()).name(province.getName()).build();
  }
}
