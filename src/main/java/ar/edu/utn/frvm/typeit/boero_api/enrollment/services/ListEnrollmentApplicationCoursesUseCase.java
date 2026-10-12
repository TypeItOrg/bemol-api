package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentCourseOptionResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentApplicationCoursesUseCase {

  private final EnrollmentApplicationRepository applicationRepository;
  private final CourseRepository courseRepository;
  private final java.time.Clock clock;
  private final AcademicEligibilityService eligibilityService;
  private final CourseCapacityService capacityService;

  @Transactional(readOnly = true)
  public Page<EnrollmentCourseOptionResponse> execute(
      final UUID personId,
      final UUID applicationId,
      final @Nullable String search,
      final @Nullable UUID studyPlanSpaceId,
      final @Nullable Integer academicYear,
      final Pageable pageable) {
    final var application =
        applicationRepository
            .findById(applicationId)
            .filter(candidate -> candidate.getDeletedAt() == null)
            .filter(candidate -> candidate.getApplicantPerson().getId().equals(personId))
            .orElseThrow(EnrollmentApplicationNotFoundException::new);
    if (application.getTrainingPath() == null) {
      return Page.empty(pageable);
    }

    final var courses =
        courseRepository.findOpenForEnrollment(
            application.getInstitution().getId(),
            application.getTrainingPath().getId(),
            application.getId(),
            personId,
            application.getEnrollmentPeriod() == null
                ? null
                : application.getEnrollmentPeriod().getId(),
            clock.instant(),
            studyPlanSpaceId,
            application.getAcademicYear() == null
                ? academicYear
                : application.getAcademicYear().getYear(),
            search == null || search.isBlank() ? null : search.trim(),
            pageable);
    final var coursesWithCapacity =
        capacityService.findCoursesWithCapacity(
            application.getInstitution().getId(), courses.getContent());

    final var eligibility =
        eligibilityService.evaluateCourses(
            application.getInstitution().getId(), personId, courses.getContent());

    return courses.map(
        course ->
            EnrollmentCourseOptionResponse.from(
                course,
                coursesWithCapacity.contains(course.getId()),
                requireNonNull(eligibility.get(course.getId()))));
  }
}
