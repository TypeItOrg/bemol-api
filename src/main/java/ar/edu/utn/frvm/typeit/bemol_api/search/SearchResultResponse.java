package ar.edu.utn.frvm.typeit.bemol_api.search;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SearchResultResponse(
    UUID id,
    @Nullable UUID institutionId,
    @Nullable String institutionName,
    @Nullable Boolean institutionActive,
    String title,
    @Nullable String subtitle,
    @Nullable String status,
    @Nullable String category,
    @Nullable Integer studyPlanVersion) {}
