package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record StartEnrollmentApplicationRequest(
    @NotNull(message = EnrollmentMessages.COURSE_SELECTION_REQUIRED)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable UUID trainingPathId,
    @Schema(nullable = true) @Nullable UUID studyPlanId,
    @Schema(nullable = true) @Nullable UUID academicYearId,
    @Schema(nullable = true) @Nullable UUID enrollmentPeriodId) {
  public StartEnrollmentApplicationRequest() {
    this(null, null, null, null);
  }

  public StartEnrollmentApplicationRequest(final UUID studyPlanId, final UUID academicYearId) {
    this(null, studyPlanId, academicYearId, null);
  }

  public StartEnrollmentApplicationRequest(
      @Nullable UUID trainingPathId, @Nullable UUID studyPlanId, @Nullable UUID academicYearId) {
    this(trainingPathId, studyPlanId, academicYearId, null);
  }

  public @Nullable UUID getTrainingPathId() {
    return trainingPathId;
  }

  public @Nullable UUID getStudyPlanId() {
    return studyPlanId;
  }

  public @Nullable UUID getAcademicYearId() {
    return academicYearId;
  }
}
