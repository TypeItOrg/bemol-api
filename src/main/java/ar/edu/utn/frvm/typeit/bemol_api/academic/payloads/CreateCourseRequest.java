package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateCourseRequest(
    @Nullable UUID studyPlanSpaceId,
    @Nullable UUID instrumentId,
    @Nullable UUID studyPlanId,
    @Nullable UUID academicSpaceId,
    @NotNull UUID academicYearId,
    @NotEmpty @Valid List<@NotNull @Valid CourseClassRequest> classes) {

  public CreateCourseRequest(
      final @Nullable UUID studyPlanId,
      final @Nullable UUID academicSpaceId,
      final UUID academicYearId,
      final List<CourseClassRequest> classes) {
    this(null, null, studyPlanId, academicSpaceId, academicYearId, classes);
  }
}
