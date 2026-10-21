package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(requiredProperties = {"studyPlanSpaceIds"})
public record AcademicSpaceSelectionDto(
    @Schema(nullable = true) @Nullable List<UUID> studyPlanSpaceIds) {
  public AcademicSpaceSelectionDto() {
    this(new ArrayList<>());
  }

  public AcademicSpaceSelectionDto {
    studyPlanSpaceIds = studyPlanSpaceIds == null ? new ArrayList<>() : studyPlanSpaceIds;
  }

  public @Nullable List<UUID> getStudyPlanSpaceIds() {
    return studyPlanSpaceIds;
  }
}
