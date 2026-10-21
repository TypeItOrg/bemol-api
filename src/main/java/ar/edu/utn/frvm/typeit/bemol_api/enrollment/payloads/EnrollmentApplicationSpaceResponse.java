package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(
    requiredProperties = {
      "studyPlanSpaceId",
      "spaceName",
      "academicLevelName",
      "instrumentId",
      "instrumentName"
    })
public record EnrollmentApplicationSpaceResponse(
    @Schema(nullable = true) @Nullable UUID studyPlanSpaceId,
    @Schema(nullable = true) @Nullable String spaceName,
    @Schema(nullable = true) @Nullable String academicLevelName,
    @Schema(nullable = true) @Nullable UUID instrumentId,
    @Schema(nullable = true) @Nullable String instrumentName) {
  public EnrollmentApplicationSpaceResponse() {
    this(null, null, null, null, null);
  }

  public @Nullable UUID getStudyPlanSpaceId() {
    return studyPlanSpaceId;
  }

  public @Nullable String getSpaceName() {
    return spaceName;
  }

  public @Nullable String getAcademicLevelName() {
    return academicLevelName;
  }

  public @Nullable UUID getInstrumentId() {
    return instrumentId;
  }

  public @Nullable String getInstrumentName() {
    return instrumentName;
  }
}
