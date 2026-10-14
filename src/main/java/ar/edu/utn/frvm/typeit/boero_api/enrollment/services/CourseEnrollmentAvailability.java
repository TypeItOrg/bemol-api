package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentSchedules.Assignment;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentAvailability {
  private final CourseEnrollmentRepository enrollments;
  private final CourseEnrollmentScheduleRepository schedules;

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public void requireAvailable(
      final UUID institutionId,
      final Student student,
      final Course course,
      final List<Assignment> assignments) {
    if (enrollments
        .findByStudentAndCourseAndStatus(
            institutionId, student.getId(), course.getId(), CourseEnrollmentStatus.ENROLLED)
        .isPresent()) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ALREADY_ENROLLED);
    }

    requireCapacity(institutionId, assignments);
    requireCompatibleSchedules(institutionId, student, course, assignments);
  }

  private void requireCapacity(final UUID institutionId, final List<Assignment> assignments) {
    for (final var assignment : assignments) {
      final int occupancy =
          assignment.slot() == null
              ? schedules.findActiveByDay(institutionId, assignment.day().getId()).size()
              : schedules
                  .findActiveByIndividualSlot(institutionId, assignment.slot().getId())
                  .size();
      final var capacity = assignment.day().getCapacity();
      final boolean full =
          assignment.slot() != null ? occupancy > 0 : capacity != null && occupancy >= capacity;
      if (full) {
        throw new EnrollmentValidationException(EnrollmentMessages.COURSE_CAPACITY_EXCEEDED);
      }
    }
  }

  private void requireCompatibleSchedules(
      final UUID institutionId,
      final Student student,
      final Course course,
      final List<Assignment> assignments) {
    final var existing = schedules.findActiveByStudent(institutionId, student.getId());
    for (final var requested : assignments) {
      for (final var current : existing) {
        final boolean conflict =
            academicYearsOverlap(course, current.getCourseEnrollment().getCourse())
                && current.getDayOfWeek() == requested.dayOfWeek()
                && requested.startTime().isBefore(current.getEndTime())
                && current.getStartTime().isBefore(requested.endTime());
        if (conflict) {
          throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ASSIGNMENT_CONFLICT);
        }
      }
    }
  }

  private static boolean academicYearsOverlap(final Course requested, final Course existing) {
    final var requestedStart = requested.getAcademicYear().getStartDate();
    final var requestedEnd = requested.getAcademicYear().getEndDate();
    final var existingStart = existing.getAcademicYear().getStartDate();
    final var existingEnd = existing.getAcademicYear().getEndDate();

    // Missing bounds cannot prove that the years are disjoint.
    return requestedStart == null
        || requestedEnd == null
        || existingStart == null
        || existingEnd == null
        || !requestedEnd.isBefore(existingStart) && !existingEnd.isBefore(requestedStart);
  }
}
