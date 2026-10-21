package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InstitutionListItemResponse(
    UUID id,
    String name,
    String slug,
    CountryLocationResponse country,
    String city,
    String province,
    boolean active) {

  public static InstitutionListItemResponse from(Institution institution) {
    return InstitutionListItemResponse.builder()
        .id(institution.getId())
        .name(institution.getName())
        .slug(institution.getSlug())
        .country(CountryLocationResponse.from(institution.getCity().getProvince().getCountry()))
        .city(institution.getCity().getName())
        .province(institution.getCity().getProvince().getName())
        .active(institution.isActive())
        .build();
  }
}
