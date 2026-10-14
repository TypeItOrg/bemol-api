package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentHistory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationCourseAssignmentSnapshot;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentHistoryRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationCourseAssignmentSnapshotRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentSchedules.Assignment;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentJournal {
  private final CourseEnrollmentHistoryRepository history;
  private final EnrollmentApplicationCourseAssignmentSnapshotRepository snapshots;
  private final CourseEnrollmentRepository enrollments;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final CourseEnrollment enrollment,
      final @Nullable CourseEnrollmentStatus previousStatus,
      final CourseEnrollmentStatus newStatus,
      final @Nullable AcademicEnrollmentStatus previousAcademicStatus,
      final AcademicEnrollmentStatus newAcademicStatus,
      final String operation,
      final @Nullable String reason,
      final @Nullable UUID authorityPersonId) {
    history.save(
        CourseEnrollmentHistory.create(
            institution,
            enrollment,
            previousStatus,
            newStatus,
            previousAcademicStatus,
            newAcademicStatus,
            operation,
            reason,
            authorityPersonId,
            clock.instant()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void snapshot(
      final Institution institution,
      final EnrollmentApplicationCourse applicationCourse,
      final CourseClass courseClass,
      final List<Assignment> assignments,
      final @Nullable UUID authorityPersonId) {
    final var course = applicationCourse.getCourse();
    final var space = course.getStudyPlanSpace();
    final var level = space.getAcademicLevel();
    final var instrument = course.getInstrument();

    for (final var assignment : assignments) {
      snapshots.save(
          EnrollmentApplicationCourseAssignmentSnapshot.builder()
              .institution(institution)
              .applicationCourse(applicationCourse)
              .course(course)
              .courseClass(courseClass)
              .schedule(assignment.schedule())
              .individualSlotId(assignment.slot() == null ? null : assignment.slot().getId())
              .courseName(space.getAcademicSpace().getName())
              .trainingPathName(space.getStudyPlan().getTrainingPath().getName())
              .studyPlanName(space.getStudyPlan().getName())
              .academicSpaceName(space.getAcademicSpace().getName())
              .academicLevelName(level == null ? null : level.getName())
              .instrumentName(instrument == null ? null : instrument.getName())
              .dayOfWeek(assignment.dayOfWeek())
              .startTime(assignment.startTime())
              .endTime(assignment.endTime())
              .assignedAt(clock.instant())
              .assignedByPersonId(authorityPersonId)
              .operation("APPLICATION_ENROLLMENT")
              .build());
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void flush() {
    try {
      enrollments.flush();
    } catch (DataIntegrityViolationException exception) {
      throw EnrollmentIntegrityViolationTranslator.courseEnrollment(exception);
    }
  }
}
