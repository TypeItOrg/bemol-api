package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProvinceSummaryResponse(UUID provinceId, String name) {

  public static ProvinceSummaryResponse from(Province province) {
    return ProvinceSummaryResponse.builder()
        .provinceId(province.getId())
        .name(province.getName())
        .build();
  }
}
