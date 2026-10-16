package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
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
      "updatedAt"
    })
public record CourseEnrollmentGradeResponse(
    UUID id,
    String evaluation,
    BigDecimal value,
    CourseEnrollmentGradePublicationStatus publicationStatus,
    @Schema(nullable = true) @Nullable String publishedEvaluation,
    @Schema(nullable = true) @Nullable BigDecimal publishedValue,
    @Schema(nullable = true) @Nullable PersonAudit createdBy,
    Instant createdAt,
    @Schema(nullable = true) @Nullable PersonAudit updatedBy,
    Instant updatedAt,
    @Schema(nullable = true) @Nullable PersonAudit publishedBy,
    @Schema(nullable = true) @Nullable Instant publishedAt,
    long version) {

  public record PersonAudit(UUID id, String fullName) {}

  public static CourseEnrollmentGradeResponse from(
      final CourseEnrollmentGrade grade, final Map<UUID, Person> people) {
    return new CourseEnrollmentGradeResponse(
        grade.getId(),
        grade.getEvaluation(),
        grade.getValue(),
        grade.publicationStatus(),
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        audit(grade.getCreatedByPersonId(), people, grade.getCreatedAt()),
        grade.getCreatedAt(),
        audit(grade.getUpdatedByPersonId(), people, grade.getUpdatedAt()),
        grade.getUpdatedAt(),
        audit(grade.getPublishedByPersonId(), people, grade.getPublishedAt()),
        grade.getPublishedAt(),
        grade.getVersion());
  }

  private static @Nullable PersonAudit audit(
      final @Nullable UUID personId,
      final Map<UUID, Person> people,
      final @Nullable Instant fallback) {
    if (personId == null) {
      return null;
    }

    final Person person = people.get(personId);

    if (person == null) {
      return new PersonAudit(personId, "Usuario");
    }

    return new PersonAudit(personId, person.getFirstName() + " " + person.getLastName());
  }
}
