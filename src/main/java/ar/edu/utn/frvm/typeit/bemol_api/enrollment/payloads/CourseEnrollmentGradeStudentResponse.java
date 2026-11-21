package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Schema(requiredProperties = {"id", "evaluation", "value"})
public record CourseEnrollmentGradeStudentResponse(UUID id, String evaluation, BigDecimal value) {

  public static CourseEnrollmentGradeStudentResponse from(final CourseEnrollmentGrade grade) {
    final String evaluation =
        Objects.requireNonNull(
            grade.getPublishedEvaluation(), "Published grade evaluation is required");
    final BigDecimal value =
        Objects.requireNonNull(grade.getPublishedValue(), "Published grade value is required");
    return new CourseEnrollmentGradeStudentResponse(grade.getId(), evaluation, value);
  }
}
