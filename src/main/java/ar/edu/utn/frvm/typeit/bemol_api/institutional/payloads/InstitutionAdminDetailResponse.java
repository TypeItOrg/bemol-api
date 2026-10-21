package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record InstitutionAdminDetailResponse(
    UUID id,
    String name,
    String slug,
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable String publicSubdomain,
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) @Nullable String logoUrl,
    CitySummaryResponse city,
    ProvinceSummaryResponse province,
    CountryLocationResponse country,
    @Nullable String street,
    @Nullable String number,
    @Nullable String neighborhood,
    @Nullable String additionalInfo,
    @Nullable String phoneNumber,
    @Nullable String email,
    boolean active,
    long userCount) {

  public static InstitutionAdminDetailResponse from(Institution institution, long userCount) {
    var city = institution.getCity();
    var province = city.getProvince();
    var country = province.getCountry();

    return InstitutionAdminDetailResponse.builder()
        .id(institution.getId())
        .name(institution.getName())
        .slug(institution.getSlug())
        .publicSubdomain(institution.getPublicSubdomain())
        .logoUrl(InstitutionPublicAccessResponse.logoUrl(institution))
        .city(CitySummaryResponse.from(city))
        .province(ProvinceSummaryResponse.from(province))
        .country(CountryLocationResponse.from(country))
        .street(institution.getStreet())
        .number(institution.getNumber())
        .neighborhood(institution.getNeighborhood())
        .additionalInfo(institution.getAdditionalInfo())
        .phoneNumber(institution.getPhoneNumber())
        .email(institution.getEmail())
        .active(institution.isActive())
        .userCount(userCount)
        .build();
  }
}
