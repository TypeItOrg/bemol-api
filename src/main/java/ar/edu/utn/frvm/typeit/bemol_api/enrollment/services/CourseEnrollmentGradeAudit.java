package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeAudit {

  private final AuditEventRecorder audit;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final CourseEnrollment enrollment,
      final UUID gradeId,
      final CourseEnrollmentGradeHistoryOperation operation,
      final @Nullable GradeSnapshot before,
      final @Nullable GradeSnapshot after) {
    final AuditOperation action = mapAction(operation);
    final String label = resolveLabel(after, before);
    audit.recordSnapshotChange(
        institution,
        enrollment.getStudent().getPerson().getId(),
        action,
        CourseEnrollmentGrade.AUDIT_TYPE,
        gradeId,
        label,
        snapshot(before),
        snapshot(after));
  }

  private static AuditOperation mapAction(final CourseEnrollmentGradeHistoryOperation operation) {
    return switch (operation) {
      case CREATED, BASELINE -> EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_CREATED;
      case UPDATED -> EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_UPDATED;
      case MARKED_FOR_DELETION -> EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_MARKED_FOR_DELETION;
      case CANCELLED_DELETION -> EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_DELETION_CANCELLED;
      case PUBLISHED -> EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_PUBLISHED;
      case DELETED_DRAFT, DELETED_PUBLISHED ->
          EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_DELETED;
      case DISCARDED_DRAFT_ON_EXIT, REVERTED_TO_PUBLISHED_ON_EXIT ->
          EnrollmentAuditAction.COURSE_ENROLLMENT_GRADE_RECONCILED_ON_EXIT;
    };
  }

  private static String resolveLabel(
      final @Nullable GradeSnapshot after, final @Nullable GradeSnapshot before) {
    if (after != null && after.evaluation() != null) {
      return after.evaluation();
    }
    if (before != null && before.evaluation() != null) {
      return before.evaluation();
    }
    return "Nota";
  }

  private static AuditSnapshot.Snapshot snapshot(final @Nullable GradeSnapshot state) {
    if (state == null) {
      return AuditSnapshot.empty();
    }
    return AuditSnapshot.fields()
        .expose("evaluation", "Evaluación", state.evaluation())
        .expose("value", "Nota", formatValue(state.value()))
        .expose("publishedEvaluation", "Evaluación publicada", state.publishedEvaluation())
        .expose("publishedValue", "Nota publicada", formatValue(state.publishedValue()))
        .expose("status", "Estado", statusLabel(state.status()))
        .build();
  }

  private static @Nullable String formatValue(final @Nullable BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  private static @Nullable String statusLabel(
      final @Nullable CourseEnrollmentGradePublicationStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case DRAFT -> "Borrador";
      case PUBLISHED -> "Publicada";
      case PENDING_CHANGES -> "Cambios pendientes";
      case PENDING_DELETION -> "Pendiente de eliminación";
    };
  }

  public record GradeSnapshot(
      @Nullable String evaluation,
      @Nullable BigDecimal value,
      @Nullable String publishedEvaluation,
      @Nullable BigDecimal publishedValue,
      @Nullable CourseEnrollmentGradePublicationStatus status) {

    public static GradeSnapshot of(final CourseEnrollmentGrade grade) {
      return new GradeSnapshot(
          grade.getEvaluation(),
          grade.getValue(),
          grade.getPublishedEvaluation(),
          grade.getPublishedValue(),
          grade.publicationStatus());
    }
  }
}
