package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CountrySummaryResponse(UUID id, String name, String isoCode) {

  public static CountrySummaryResponse from(Country country) {
    return CountrySummaryResponse.builder()
        .id(country.getId())
        .name(country.getName())
        .isoCode(country.getIsoCode())
        .build();
  }
}
