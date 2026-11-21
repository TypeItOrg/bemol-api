package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"gradeId", "lastEvaluation", "deletedAt", "deletionType"})
public record CourseEnrollmentGradeArchivedResponse(
    UUID gradeId,
    String lastEvaluation,
    @Schema(nullable = true) @Nullable BigDecimal lastValue,
    Instant deletedAt,
    CourseEnrollmentGradeHistoryOperation deletionType) {

  public static CourseEnrollmentGradeArchivedResponse from(
      final CourseEnrollmentGradeHistory event) {
    final String evaluation =
        event.getEvaluationBefore() != null
            ? event.getEvaluationBefore()
            : (event.getPublishedEvaluationBefore() != null
                ? event.getPublishedEvaluationBefore()
                : "Nota");
    final @Nullable BigDecimal value =
        event.getValueBefore() != null ? event.getValueBefore() : event.getPublishedValueBefore();

    return new CourseEnrollmentGradeArchivedResponse(
        event.getGradeId(), evaluation, value, event.getChangedAt(), event.getOperation());
  }
}
