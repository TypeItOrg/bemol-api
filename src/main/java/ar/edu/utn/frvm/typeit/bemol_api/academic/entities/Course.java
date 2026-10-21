package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@AuditOverride(forClass = SoftDeletable.class, name = "deletedAt", isAudited = true)
@Table(
    name = "courses",
    uniqueConstraints =
        @UniqueConstraint(
            name = "courses_institution_id_id_unique",
            columnNames = {"institution_id", "course_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class Course extends SoftDeletable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_space_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlanSpace studyPlanSpace;

  @Column(name = "training_path_id", nullable = false, updatable = false)
  private UUID trainingPathId;

  @Column(name = "academic_space_id", nullable = false, updatable = false)
  private UUID academicSpaceId;

  @Column(name = "academic_level_id", updatable = false)
  private @Nullable UUID academicLevelId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable Instrument instrument;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private AcademicYear academicYear;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Audited(withModifiedFlag = true)
  private CourseStatus status;

  @Transient private StudyPlan legacyStudyPlan;

  @Transient private AcademicSpace legacyAcademicSpace;

  public StudyPlan getStudyPlan() {
    if (studyPlanSpace != null) {
      return studyPlanSpace.getStudyPlan();
    }

    return legacyStudyPlan;
  }

  public AcademicSpace getAcademicSpace() {
    if (studyPlanSpace != null) {
      return studyPlanSpace.getAcademicSpace();
    }

    return legacyAcademicSpace;
  }

  public static Course create(
      final Institution institution,
      final StudyPlanSpace studyPlanSpace,
      final AcademicYear academicYear) {
    return create(institution, studyPlanSpace, academicYear, null);
  }

  public static Course create(
      final Institution institution,
      final StudyPlanSpace studyPlanSpace,
      final AcademicYear academicYear,
      final @Nullable Instrument instrument) {
    return Course.builder()
        .institution(institution)
        .studyPlanSpace(studyPlanSpace)
        .trainingPathId(studyPlanSpace.getStudyPlan().getTrainingPath().getId())
        .academicSpaceId(studyPlanSpace.getAcademicSpace().getId())
        .academicLevelId(
            studyPlanSpace.getAcademicLevel() == null
                ? null
                : studyPlanSpace.getAcademicLevel().getId())
        .instrument(instrument)
        .academicYear(academicYear)
        .status(CourseStatus.ACTIVE)
        .build();
  }

  /**
   * Compatibility factory for in-memory callers created before Course was anchored to a
   * StudyPlanSpace. Persisted courses must use the StudyPlanSpace factory above.
   */
  @Deprecated
  public static Course create(
      final Institution institution,
      final StudyPlan studyPlan,
      final AcademicSpace academicSpace,
      final AcademicYear academicYear) {
    final var course =
        Course.builder()
            .institution(institution)
            .academicYear(academicYear)
            .status(CourseStatus.ACTIVE)
            .build();
    course.legacyStudyPlan = studyPlan;
    course.legacyAcademicSpace = academicSpace;
    return course;
  }

  public void activate() {
    if (status == CourseStatus.CLOSED) {
      throw new InvalidAcademicStateException();
    }
    ensureParentsActive();
    status = CourseStatus.ACTIVE;
  }

  public void deactivate() {
    if (status == CourseStatus.CLOSED) {
      throw new InvalidAcademicStateException();
    }
    status = CourseStatus.INACTIVE;
  }

  public void close() {
    if (status == CourseStatus.CLOSED) {
      return;
    }
    status = CourseStatus.CLOSED;
  }

  public boolean isActive() {
    return status == CourseStatus.ACTIVE;
  }

  public boolean isClosed() {
    return status == CourseStatus.CLOSED;
  }

  public void updateStatus(final CourseStatus target) {
    if (target == status) {
      return;
    }
    if (target == CourseStatus.CLOSED) {
      close();
      return;
    }
    if (status == CourseStatus.CLOSED) {
      throw new InvalidAcademicStateException();
    }
    final boolean validTransition =
        (status == CourseStatus.ACTIVE && target == CourseStatus.INACTIVE)
            || (status == CourseStatus.INACTIVE && target == CourseStatus.ACTIVE);
    if (!validTransition) {
      throw new InvalidAcademicStateException();
    }
    if (target == CourseStatus.ACTIVE) {
      ensureParentsActive();
    }
    status = target;
  }

  public boolean delete(final Instant deletedAt) {
    if (status == CourseStatus.CLOSED) {
      throw new InvalidAcademicStateException();
    }
    if (status == CourseStatus.ACTIVE) {
      throw new InvalidAcademicStateException();
    }
    return markDeleted(deletedAt);
  }

  @Override
  public boolean restore() {
    if (status == CourseStatus.CLOSED) {
      throw new InvalidAcademicStateException();
    }
    if (isDeleted()) {
      ensureParentsActive();
      if (getAcademicSpace().isInstrumental() != (instrument != null)) {
        throw new AcademicConflictException(AcademicMessages.COURSE_RESTORE_INSTRUMENT_MISMATCH);
      }
    }
    return super.restore();
  }

  private void ensureAcademicYearActive() {
    if (academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
      throw new InvalidAcademicStateException();
    }
  }

  private void ensureParentsActive() {
    if (getStudyPlan().getStatus() != StudyPlanStatus.ACTIVE
        || getStudyPlan().getDeletedAt() != null
        || !getStudyPlan().getTrainingPath().isActive()
        || getStudyPlan().getTrainingPath().getDeletedAt() != null) {
      throw new InvalidAcademicStateException();
    }
    ensureAcademicYearActive();
  }
}
