package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentPeriodClosedException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentPeriodRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentApplicationPeriodService {
  private final EnrollmentPeriodRepository periodRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public boolean hasOpenCourses(
      final UUID institutionId, final UUID trainingPathId, final @Nullable UUID academicYearId) {
    return periodRepository
        .findAvailableOffers(
            institutionId,
            clock.instant(),
            trainingPathId,
            academicYearId,
            null,
            Pageable.ofSize(1))
        .hasContent();
  }

  @Transactional(readOnly = true)
  public boolean isOpen(final EnrollmentApplication application) {
    final var year = application.getAcademicYear();
    if (year != null && year.getStatus() != AcademicYearStatus.ACTIVE) {
      return false;
    }

    if (application.getEnrollmentPeriod() != null) {
      return application.getEnrollmentPeriod().isOpenAt(clock.instant());
    }
    return hasOpenCourses(
        application.getInstitution().getId(),
        application.getTrainingPathId(),
        year == null ? null : year.getId());
  }

  @Transactional(readOnly = true)
  public void requireOpen(final EnrollmentApplication application) {
    if (!isOpen(application)) {
      throw new EnrollmentPeriodClosedException();
    }
  }

  @Transactional(readOnly = true)
  public EnrollmentPeriod resolveCoursePeriod(
      final EnrollmentApplication application,
      final Course course,
      final @Nullable EnrollmentPeriod assigned) {
    final var year = application.getAcademicYear();
    if (year != null && !year.getId().equals(course.getAcademicYear().getId())) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_OUTSIDE_ACADEMIC_YEAR);
    }

    final var original = assigned != null ? assigned : application.getEnrollmentPeriod();
    if (original != null) {
      if (!original.isOpenAt(clock.instant())) {
        throw new EnrollmentPeriodClosedException();
      }
      if (!original.includes(course.getStudyPlanSpace())
          || !original.getAcademicYear().getId().equals(course.getAcademicYear().getId())) {
        throw new EnrollmentValidationException(EnrollmentMessages.COURSE_OUTSIDE_PERIOD);
      }
      return original;
    }
    final var periods =
        periodRepository.findOpenForCourse(
            application.getInstitution().getId(), course.getId(), clock.instant());
    if (periods.size() != 1) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_OUTSIDE_PERIOD);
    }
    return periods.getFirst();
  }
}
