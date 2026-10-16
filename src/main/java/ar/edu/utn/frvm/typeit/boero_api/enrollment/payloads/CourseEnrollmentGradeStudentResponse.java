package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(requiredProperties = {"id", "evaluation", "value"})
public record CourseEnrollmentGradeStudentResponse(UUID id, String evaluation, BigDecimal value) {

  public static CourseEnrollmentGradeStudentResponse from(final CourseEnrollmentGrade grade) {
    return new CourseEnrollmentGradeStudentResponse(
        grade.getId(),
        grade.getPublishedEvaluation() == null
            ? grade.getEvaluation()
            : grade.getPublishedEvaluation(),
        grade.getPublishedValue() == null ? grade.getValue() : grade.getPublishedValue());
  }
}
