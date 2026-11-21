package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeAudit.GradeSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles grades when an enrollment leaves {@code ENROLLED}: never-published drafts are
 * discarded and unpublished working changes are rolled back to the published snapshot.
 */
@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeFinalization {

  private static final String TEMPORARY_TITLE_PREFIX = "__exit_restore__";

  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeJournal journal;

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
    reconcileEnrollmentGrades(institution, enrollment, grades, actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void reconcileOnEnrollmentsExit(
      final Institution institution,
      final List<CourseEnrollment> enrollments,
      final @Nullable UUID actorPersonId) {
    final List<CourseEnrollment> exited =
        enrollments.stream()
            .filter(enrollment -> enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED)
            .toList();

    if (exited.isEmpty()) {
      return;
    }

    final List<UUID> enrollmentIds = exited.stream().map(enrollment -> enrollment.getId()).toList();
    final Map<UUID, List<CourseEnrollmentGrade>> gradesByEnrollment =
        gradeRepository.findByEnrollmentIdsForUpdate(institution.getId(), enrollmentIds).stream()
            .collect(Collectors.groupingBy(grade -> grade.getCourseEnrollment().getId()));

    for (final CourseEnrollment enrollment : exited) {
      final List<CourseEnrollmentGrade> grades =
          gradesByEnrollment.getOrDefault(enrollment.getId(), List.of());
      if (!grades.isEmpty()) {
        reconcileEnrollmentGrades(institution, enrollment, grades, actorPersonId);
      }
    }
  }

  private void reconcileEnrollmentGrades(
      final Institution institution,
      final CourseEnrollment enrollment,
      final List<CourseEnrollmentGrade> grades,
      final @Nullable UUID actorPersonId) {
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
      journal.recordDiscardedDraftOnExit(institution, enrollment, draft, actorPersonId);
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
      throw new EnrollmentValidationException(GradeMessages.EXIT_PUBLISHED_TITLE_CONFLICT);
    }

    for (final RestorePlan plan : plans) {
      journal.recordRevertedOnExit(
          institution, enrollment, plan.grade(), plan.before(), actorPersonId);
    }
  }

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
    final String evaluationBefore = Objects.requireNonNull(plan.before().evaluation());

    if (normalizedTitle(evaluationBefore).equals(normalizedTitle(plan.snapshotEvaluation()))) {
      return;
    }

    grade.cancelDeletion(actorPersonId);
    grade.updateWorkingCopy(
        TEMPORARY_TITLE_PREFIX + grade.getId(),
        Objects.requireNonNull(plan.before().value()),
        actorPersonId);
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
    return evaluation.strip().toLowerCase(Locale.ROOT);
  }

  private record RestorePlan(
      CourseEnrollmentGrade grade, GradeSnapshot before, String snapshotEvaluation) {

    static RestorePlan of(final CourseEnrollmentGrade grade) {
      return new RestorePlan(
          grade, GradeSnapshot.of(grade), Objects.requireNonNull(grade.getPublishedEvaluation()));
    }
  }
}
