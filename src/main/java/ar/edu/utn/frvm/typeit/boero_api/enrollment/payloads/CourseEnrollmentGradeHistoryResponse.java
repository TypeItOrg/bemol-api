package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"historyId", "gradeId", "operation", "changedAt"})
public record CourseEnrollmentGradeHistoryResponse(
    UUID historyId,
    UUID gradeId,
    CourseEnrollmentGradeHistoryOperation operation,
    @Schema(nullable = true) @Nullable String evaluationBefore,
    @Schema(nullable = true) @Nullable BigDecimal valueBefore,
    @Schema(nullable = true) @Nullable String evaluationAfter,
    @Schema(nullable = true) @Nullable BigDecimal valueAfter,
    @Schema(nullable = true) @Nullable String publishedEvaluationBefore,
    @Schema(nullable = true) @Nullable BigDecimal publishedValueBefore,
    @Schema(nullable = true) @Nullable String publishedEvaluationAfter,
    @Schema(nullable = true) @Nullable BigDecimal publishedValueAfter,
    @Schema(nullable = true) @Nullable CourseEnrollmentGradePublicationStatus previousStatus,
    @Schema(nullable = true) @Nullable CourseEnrollmentGradePublicationStatus newStatus,
    @Schema(nullable = true) @Nullable PersonAudit actor,
    Instant changedAt) {

  public record PersonAudit(UUID id, String fullName) {}

  public static CourseEnrollmentGradeHistoryResponse from(
      final CourseEnrollmentGradeHistory event, final Map<UUID, Person> people) {
    return new CourseEnrollmentGradeHistoryResponse(
        event.getId(),
        event.getGradeId(),
        event.getOperation(),
        event.getEvaluationBefore(),
        event.getValueBefore(),
        event.getEvaluationAfter(),
        event.getValueAfter(),
        event.getPublishedEvaluationBefore(),
        event.getPublishedValueBefore(),
        event.getPublishedEvaluationAfter(),
        event.getPublishedValueAfter(),
        event.getPreviousStatus(),
        event.getNewStatus(),
        audit(event.getActorPersonId(), people),
        event.getChangedAt());
  }

  private static @Nullable PersonAudit audit(
      final @Nullable UUID personId, final Map<UUID, Person> people) {
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
