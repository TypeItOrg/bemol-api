package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import java.util.UUID;
import lombok.Builder;

@Builder
public record CitySummaryResponse(UUID id, String name, UUID provinceId, String province) {}
