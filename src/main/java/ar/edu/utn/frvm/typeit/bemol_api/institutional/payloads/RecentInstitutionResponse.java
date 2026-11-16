package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record RecentInstitutionResponse(
    UUID id,
    String name,
    String city,
    String province,
    boolean active,
    Instant createdAt,
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable String logoUrl) {

  public static RecentInstitutionResponse from(final Institution institution) {
    return RecentInstitutionResponse.builder()
        .id(institution.getId())
        .name(institution.getName())
        .city(institution.getCity().getName())
        .province(institution.getCity().getProvince().getName())
        .active(institution.isActive())
        .createdAt(institution.getCreatedAt())
        .logoUrl(InstitutionPublicAccessResponse.logoUrl(institution))
        .build();
  }
}
