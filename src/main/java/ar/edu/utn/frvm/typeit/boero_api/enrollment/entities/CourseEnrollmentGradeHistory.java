package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "course_enrollment_grade_histories",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "course_enrollment_grade_histories_tenant_id_unique",
          columnNames = {"institution_id", "course_enrollment_grade_history_id"})
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CourseEnrollmentGradeHistory extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_enrollment_grade_history_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_enrollment_id", nullable = false)
  private CourseEnrollment courseEnrollment;

  @Column(name = "course_enrollment_grade_id", nullable = false)
  private UUID gradeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private CourseEnrollmentGradeHistoryOperation operation;

  @Column(name = "evaluation_before", length = 150)
  private @Nullable String evaluationBefore;

  @Column(name = "value_before", precision = 4, scale = 2)
  private @Nullable BigDecimal valueBefore;

  @Column(name = "evaluation_after", length = 150)
  private @Nullable String evaluationAfter;

  @Column(name = "value_after", precision = 4, scale = 2)
  private @Nullable BigDecimal valueAfter;

  @Column(name = "published_evaluation_before", length = 150)
  private @Nullable String publishedEvaluationBefore;

  @Column(name = "published_value_before", precision = 4, scale = 2)
  private @Nullable BigDecimal publishedValueBefore;

  @Column(name = "published_evaluation_after", length = 150)
  private @Nullable String publishedEvaluationAfter;

  @Column(name = "published_value_after", precision = 4, scale = 2)
  private @Nullable BigDecimal publishedValueAfter;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", length = 30)
  private @Nullable CourseEnrollmentGradePublicationStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", length = 30)
  private @Nullable CourseEnrollmentGradePublicationStatus newStatus;

  @Column(name = "actor_person_id")
  private @Nullable UUID actorPersonId;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  public static CourseEnrollmentGradeHistory create(
      final Institution institution,
      final CourseEnrollment enrollment,
      final UUID gradeId,
      final CourseEnrollmentGradeHistoryOperation operation,
      final @Nullable String evaluationBefore,
      final @Nullable BigDecimal valueBefore,
      final @Nullable String evaluationAfter,
      final @Nullable BigDecimal valueAfter,
      final @Nullable String publishedEvaluationBefore,
      final @Nullable BigDecimal publishedValueBefore,
      final @Nullable String publishedEvaluationAfter,
      final @Nullable BigDecimal publishedValueAfter,
      final @Nullable CourseEnrollmentGradePublicationStatus previousStatus,
      final @Nullable CourseEnrollmentGradePublicationStatus newStatus,
      final @Nullable UUID actorPersonId,
      final Instant changedAt) {
    return CourseEnrollmentGradeHistory.builder()
        .institution(institution)
        .courseEnrollment(enrollment)
        .gradeId(gradeId)
        .operation(operation)
        .evaluationBefore(evaluationBefore)
        .valueBefore(valueBefore)
        .evaluationAfter(evaluationAfter)
        .valueAfter(valueAfter)
        .publishedEvaluationBefore(publishedEvaluationBefore)
        .publishedValueBefore(publishedValueBefore)
        .publishedEvaluationAfter(publishedEvaluationAfter)
        .publishedValueAfter(publishedValueAfter)
        .previousStatus(previousStatus)
        .newStatus(newStatus)
        .actorPersonId(actorPersonId)
        .changedAt(changedAt)
        .build();
  }
}
