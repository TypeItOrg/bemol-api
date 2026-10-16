package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    name = "course_enrollment_grades",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "course_enrollment_grades_tenant_id_unique",
          columnNames = {"institution_id", "course_enrollment_grade_id"})
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CourseEnrollmentGrade extends Auditable {

  public static final int EVALUATION_MAX_LENGTH = 150;

  private static final BigDecimal MIN_VALUE = new BigDecimal("1");
  private static final BigDecimal MAX_VALUE = new BigDecimal("10");

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_enrollment_grade_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_enrollment_id", nullable = false)
  private CourseEnrollment courseEnrollment;

  @Column(nullable = false, length = EVALUATION_MAX_LENGTH)
  private String evaluation;

  @Column(nullable = false, precision = 4, scale = 2)
  private BigDecimal value;

  @Column(name = "published_evaluation", length = EVALUATION_MAX_LENGTH)
  private @Nullable String publishedEvaluation;

  @Column(name = "published_value", precision = 4, scale = 2)
  private @Nullable BigDecimal publishedValue;

  @Column(name = "pending_deletion", nullable = false)
  @Builder.Default
  private boolean pendingDeletion = false;

  @Column(name = "created_by_person_id")
  private @Nullable UUID createdByPersonId;

  @Column(name = "updated_by_person_id")
  private @Nullable UUID updatedByPersonId;

  @Column(name = "published_by_person_id")
  private @Nullable UUID publishedByPersonId;

  @Column(name = "published_at")
  private @Nullable Instant publishedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public static CourseEnrollmentGrade create(
      final Institution institution,
      final CourseEnrollment courseEnrollment,
      final String evaluation,
      final BigDecimal value,
      final @Nullable UUID actorPersonId) {
    final String normalizedEvaluation = normalizeEvaluation(evaluation);
    final BigDecimal normalizedValue = normalizeValue(value);

    return CourseEnrollmentGrade.builder()
        .institution(institution)
        .courseEnrollment(courseEnrollment)
        .evaluation(normalizedEvaluation)
        .value(normalizedValue)
        .publishedEvaluation(null)
        .publishedValue(null)
        .pendingDeletion(false)
        .createdByPersonId(actorPersonId)
        .updatedByPersonId(actorPersonId)
        .publishedByPersonId(null)
        .publishedAt(null)
        .build();
  }

  public void requireEditable() {
    if (pendingDeletion) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_PENDING_DELETION_IMMUTABLE);
    }
  }

  public void updateWorkingCopy(
      final String evaluation, final BigDecimal value, final @Nullable UUID actorPersonId) {
    requireEditable();

    this.evaluation = normalizeEvaluation(evaluation);
    this.value = normalizeValue(value);
    this.updatedByPersonId = actorPersonId;
  }

  public void markForDeletion(final @Nullable UUID actorPersonId) {
    if (publishedEvaluation == null) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_DRAFT_DELETE_DIRECTLY);
    }

    if (pendingDeletion) {
      return;
    }

    this.pendingDeletion = true;
    this.updatedByPersonId = actorPersonId;
  }

  public void cancelDeletion(final @Nullable UUID actorPersonId) {
    if (!pendingDeletion) {
      return;
    }

    this.pendingDeletion = false;
    this.updatedByPersonId = actorPersonId;
  }

  public void publish(final UUID publisherPersonId, final Instant publishedAt) {
    if (pendingDeletion) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_PENDING_DELETION_PUBLISH);
    }

    this.publishedEvaluation = evaluation;
    this.publishedValue = value;
    this.publishedByPersonId = publisherPersonId;
    this.publishedAt = publishedAt;
    this.updatedByPersonId = publisherPersonId;
  }

  public boolean isDraft() {
    return publishedEvaluation == null;
  }

  public boolean hasPendingChanges() {
    if (pendingDeletion) {
      return true;
    }

    if (publishedEvaluation == null || publishedValue == null) {
      return true;
    }

    return !evaluation.equals(publishedEvaluation) || value.compareTo(publishedValue) != 0;
  }

  public CourseEnrollmentGradePublicationStatus publicationStatus() {
    if (pendingDeletion) {
      return CourseEnrollmentGradePublicationStatus.PENDING_DELETION;
    }

    if (publishedEvaluation == null || publishedValue == null) {
      return CourseEnrollmentGradePublicationStatus.DRAFT;
    }

    if (!evaluation.equals(publishedEvaluation) || value.compareTo(publishedValue) != 0) {
      return CourseEnrollmentGradePublicationStatus.PENDING_CHANGES;
    }

    return CourseEnrollmentGradePublicationStatus.PUBLISHED;
  }

  public static String normalizeEvaluation(final @Nullable String evaluation) {
    if (evaluation == null) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_REQUIRED);
    }

    final String trimmed = evaluation.trim();

    if (trimmed.isEmpty()) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_REQUIRED);
    }

    if (trimmed.length() > EVALUATION_MAX_LENGTH) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_TOO_LONG);
    }

    return trimmed;
  }

  public static BigDecimal normalizeValue(final @Nullable BigDecimal value) {
    if (value == null) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_REQUIRED);
    }

    BigDecimal normalized;

    try {
      normalized = value.stripTrailingZeros();
    } catch (final ArithmeticException exception) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_INVALID);
    }

    if (normalized.scale() > 2) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_TOO_MANY_DECIMALS);
    }

    final BigDecimal plain = new BigDecimal(normalized.toPlainString());

    if (plain.compareTo(MIN_VALUE) < 0 || plain.compareTo(MAX_VALUE) > 0) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_OUT_OF_RANGE);
    }

    return plain.setScale(Math.max(0, Math.min(2, plain.scale())), RoundingMode.UNNECESSARY);
  }
}
