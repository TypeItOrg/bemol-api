package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CountryLocationResponse(UUID countryId, String name, String isoCode) {

  public static CountryLocationResponse from(Country country) {
    return CountryLocationResponse.builder()
        .countryId(country.getId())
        .name(country.getName())
        .isoCode(country.getIsoCode())
        .build();
  }
}
