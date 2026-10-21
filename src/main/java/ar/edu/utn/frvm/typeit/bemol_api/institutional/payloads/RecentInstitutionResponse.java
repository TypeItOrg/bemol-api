package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RecentInstitutionResponse(
    UUID id, String name, String city, String province, boolean active, Instant createdAt) {

  public static RecentInstitutionResponse from(final Institution institution) {
    return RecentInstitutionResponse.builder()
        .id(institution.getId())
        .name(institution.getName())
        .city(institution.getCity().getName())
        .province(institution.getCity().getProvince().getName())
        .active(institution.isActive())
        .createdAt(institution.getCreatedAt())
        .build();
  }
}
