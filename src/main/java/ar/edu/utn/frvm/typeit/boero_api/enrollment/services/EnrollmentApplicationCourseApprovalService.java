package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseWaitlistSequence;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.WaitlistReason;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseWaitlistSequenceRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentApplicationCourseApprovalService {

  private final EnrollmentApplicationCourseRepository applicationCourseRepository;
  private final CourseWaitlistSequenceRepository waitlistSequenceRepository;
  private final Clock clock;
  private final CourseCapacityService capacityService;
  private final CourseEnrollmentAudit audit;

  @Transactional
  public void process(final EnrollmentApplication application) {
    final List<EnrollmentApplicationCourse> selections =
        applicationCourseRepository.findByApplicationIdAndStatuses(
            application.getId(), List.of(EnrollmentApplicationCourseStatus.PENDING));
    selections.stream()
        .sorted(
            Comparator.comparing(
                    (EnrollmentApplicationCourse mappedEnrollmentApplicationCourse) ->
                        mappedEnrollmentApplicationCourse.getRequestedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    (EnrollmentApplicationCourse mappedEnrollmentApplicationCourse) ->
                        mappedEnrollmentApplicationCourse.getId()))
        .forEach(this::evaluate);
  }

  @Transactional
  public void reevaluateApprovedPendingForCourse(final UUID institutionId, final UUID courseId) {
    final var selections =
        applicationCourseRepository.findApprovedPendingByCourse(
            institutionId, courseId, EnrollmentApplicationCourseStatus.PENDING);
    if (selections.isEmpty()) {
      return;
    }

    final boolean hasCapacity = capacityService.hasCapacity(selections.getFirst().getCourse());
    selections.forEach(selection -> evaluate(selection, hasCapacity));
  }

  public void markSubmitted(final EnrollmentApplication application) {
    if (application.getCourseSelections() == null) {
      return;
    }
    for (final EnrollmentApplicationCourse selection : application.getCourseSelections()) {
      selection.markRequested(clock.instant(), capacityService.hasCapacity(selection.getCourse()));
    }
  }

  private void evaluate(final EnrollmentApplicationCourse selection) {
    evaluate(selection, null);
  }

  private void evaluate(
      final EnrollmentApplicationCourse selection, final @Nullable Boolean capacityOverride) {
    final var previousStatus = selection.getStatus();
    final Course course = selection.getCourse();
    if (course.isClosed()) {
      selection.reject(
          "COURSE_FINISHED", EnrollmentMessages.COURSE_NOT_ACTIVE, clock.instant(), null);
      audit.recordSelection(selection, previousStatus, false);
      return;
    }
    if (!course.isActive()) {
      return;
    }

    final boolean hasCapacity =
        capacityOverride == null
            ? capacityService.hasCapacity(selection.getCourse())
            : capacityOverride;
    if (selection.getSubmittedWithCapacity() == null) {
      selection.markRequested(clock.instant(), hasCapacity);
    }
    if (hasCapacity) {
      return;
    }

    final var sequence =
        waitlistSequenceRepository
            .findByCourseId(course.getId())
            .orElseGet(
                () ->
                    waitlistSequenceRepository.save(
                        CourseWaitlistSequence.create(course, selection.getInstitution())));
    final int waitlistNumber = sequence.nextNumber();
    selection.waitlist(
        waitlistNumber,
        capacityOverride == null
            ? WaitlistReason.NO_CAPACITY_AT_PARENT_APPROVAL
            : WaitlistReason.CAPACITY_EXHAUSTED_AFTER_APPROVAL,
        clock.instant());
    audit.recordSelection(selection, previousStatus, false);
  }
}
