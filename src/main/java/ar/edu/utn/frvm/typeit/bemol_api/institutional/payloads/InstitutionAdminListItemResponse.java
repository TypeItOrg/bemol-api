package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InstitutionAdminListItemResponse(
    UUID id,
    String name,
    CountryLocationResponse country,
    String city,
    String province,
    boolean active,
    long userCount) {

  public static InstitutionAdminListItemResponse from(Institution institution, long userCount) {
    return InstitutionAdminListItemResponse.builder()
        .id(institution.getId())
        .name(institution.getName())
        .country(CountryLocationResponse.from(institution.getCity().getProvince().getCountry()))
        .city(institution.getCity().getName())
        .province(institution.getCity().getProvince().getName())
        .active(institution.isActive())
        .userCount(userCount)
        .build();
  }
}
