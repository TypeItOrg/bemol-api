package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles grades when an enrollment leaves {@code ENROLLED}: never-published drafts are
 * discarded and unpublished working changes are rolled back to the published snapshot.
 *
 * <p>Runs inside the transition transaction ({@code MANDATORY}); the caller holds the institution
 * lock and the enrollment state already changed in the same persistence context.
 */
@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeFinalization {

  private static final String TEMPORARY_TITLE_PREFIX = "__exit_restore__";

  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeExitJournal exitJournal;

  @Transactional(propagation = Propagation.MANDATORY)
  public void reconcileOnEnrollmentExit(
      final Institution institution,
      final CourseEnrollment enrollment,
      final @Nullable UUID actorPersonId) {
    if (enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED) {
      return;
    }

    final List<CourseEnrollmentGrade> grades =
        gradeRepository.findByEnrollmentForUpdate(institution.getId(), enrollment.getId());

    final List<CourseEnrollmentGrade> drafts = new ArrayList<>();
    final List<CourseEnrollmentGrade> retained = new ArrayList<>();

    for (final CourseEnrollmentGrade grade : grades) {
      if (grade.isDraft()) {
        drafts.add(grade);
      } else {
        retained.add(grade);
      }
    }

    final List<CourseEnrollmentGrade> restorable =
        retained.stream().filter(grade -> grade.hasPendingChanges()).toList();

    if (drafts.isEmpty() && restorable.isEmpty()) {
      return;
    }

    final List<RestorePlan> plans = restorable.stream().map(RestorePlan::of).toList();

    if (!plans.isEmpty()) {
      requireDistinctFinalTitles(retained);
    }

    for (final CourseEnrollmentGrade draft : drafts) {
      exitJournal.recordDiscardedDraft(institution, enrollment, draft, actorPersonId);
      gradeRepository.delete(draft);
    }

    if (!drafts.isEmpty()) {
      gradeRepository.flush();
    }

    if (requiresTemporaryTitles(plans, retained)) {
      for (final RestorePlan plan : plans) {
        parkTitleChangingGrade(plan, actorPersonId);
      }

      gradeRepository.flush();
    }

    for (final RestorePlan plan : plans) {
      plan.grade().revertToPublishedOnExit(actorPersonId);
    }

    try {
      gradeRepository.flush();
    } catch (final DataIntegrityViolationException exception) {
      // Defense in depth: the pre-check normalizes in Java, the index uses lower(btrim()).
      throw new EnrollmentValidationException(GradeMessages.EXIT_PUBLISHED_TITLE_CONFLICT);
    }

    for (final RestorePlan plan : plans) {
      exitJournal.recordRevertedToPublished(
          institution,
          enrollment,
          plan.grade(),
          plan.evaluationBefore(),
          plan.valueBefore(),
          plan.previousStatus(),
          actorPersonId);
    }
  }

  /**
   * Restoring crossed working titles one by one can transiently collide with the unique index. All
   * title-changing rows are parked on internal titles first; those values are never journaled or
   * exposed because they live and die inside this transaction.
   */
  private boolean requiresTemporaryTitles(
      final List<RestorePlan> plans, final List<CourseEnrollmentGrade> retained) {
    for (final RestorePlan plan : plans) {
      final String target = normalizedTitle(plan.snapshotEvaluation());

      for (final CourseEnrollmentGrade other : retained) {
        if (other != plan.grade() && normalizedTitle(other.getEvaluation()).equals(target)) {
          return true;
        }
      }
    }

    return false;
  }

  private void parkTitleChangingGrade(final RestorePlan plan, final @Nullable UUID actorPersonId) {
    final CourseEnrollmentGrade grade = plan.grade();

    if (normalizedTitle(plan.evaluationBefore())
        .equals(normalizedTitle(plan.snapshotEvaluation()))) {
      return;
    }

    grade.cancelDeletion(actorPersonId);
    grade.updateWorkingCopy(
        TEMPORARY_TITLE_PREFIX + grade.getId(), plan.valueBefore(), actorPersonId);
  }

  private void requireDistinctFinalTitles(final List<CourseEnrollmentGrade> retained) {
    final Set<String> finalTitles = new HashSet<>();

    for (final CourseEnrollmentGrade grade : retained) {
      final String finalTitle =
          grade.hasPendingChanges()
              ? Objects.requireNonNull(grade.getPublishedEvaluation())
              : grade.getEvaluation();

      if (!finalTitles.add(normalizedTitle(finalTitle))) {
        throw new EnrollmentValidationException(GradeMessages.EXIT_PUBLISHED_TITLE_CONFLICT);
      }
    }
  }

  private static String normalizedTitle(final String evaluation) {
    return evaluation.trim().toLowerCase(Locale.ROOT);
  }

  private record RestorePlan(
      CourseEnrollmentGrade grade,
      String evaluationBefore,
      BigDecimal valueBefore,
      String snapshotEvaluation,
      CourseEnrollmentGradePublicationStatus previousStatus) {

    static RestorePlan of(final CourseEnrollmentGrade grade) {
      return new RestorePlan(
          grade,
          grade.getEvaluation(),
          grade.getValue(),
          Objects.requireNonNull(grade.getPublishedEvaluation()),
          grade.publicationStatus());
    }
  }
}
