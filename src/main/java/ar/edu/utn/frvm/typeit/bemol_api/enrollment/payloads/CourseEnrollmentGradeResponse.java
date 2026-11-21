package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "evaluation",
      "value",
      "publicationStatus",
      "createdAt",
      "updatedAt",
      "version"
    })
public record CourseEnrollmentGradeResponse(
    UUID id,
    String evaluation,
    BigDecimal value,
    CourseEnrollmentGradePublicationStatus publicationStatus,
    @Schema(nullable = true) @Nullable String publishedEvaluation,
    @Schema(nullable = true) @Nullable BigDecimal publishedValue,
    @Schema(nullable = true) @Nullable GradePersonAudit createdBy,
    Instant createdAt,
    @Schema(nullable = true) @Nullable GradePersonAudit updatedBy,
    Instant updatedAt,
    @Schema(nullable = true) @Nullable GradePersonAudit publishedBy,
    @Schema(nullable = true) @Nullable Instant publishedAt,
    long version) {

  public static CourseEnrollmentGradeResponse from(
      final CourseEnrollmentGrade grade, final Map<UUID, Person> people) {
    return new CourseEnrollmentGradeResponse(
        grade.getId(),
        grade.getEvaluation(),
        grade.getValue(),
        grade.publicationStatus(),
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        GradePersonAudit.resolve(grade.getCreatedByPersonId(), people),
        grade.getCreatedAt(),
        GradePersonAudit.resolve(grade.getUpdatedByPersonId(), people),
        grade.getUpdatedAt(),
        GradePersonAudit.resolve(grade.getPublishedByPersonId(), people),
        grade.getPublishedAt(),
        grade.getVersion());
  }
}
