package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseWaitlistCapacity;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseWaitlistEntryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseWaitlistOptionsResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCourseWaitlistUseCase {
  private final AcademicAccessGuard accessGuard;

  private final EnrollmentApplicationCourseRepository applicationCourseRepository;
  private final CourseRepository courses;
  private final CourseCapacityService capacityService;

  @Transactional(readOnly = true)
  public List<CourseWaitlistEntryResponse> execute(final UUID institutionId, final UUID courseId) {
    return execute(institutionId, courseId, null, null, null);
  }

  @Transactional(readOnly = true)
  public List<CourseWaitlistEntryResponse> execute(
      final UUID institutionId,
      final UUID courseId,
      final @Nullable String search,
      final @Nullable CourseWaitlistCapacity capacity,
      final @Nullable String shift) {
    final var course = authorizedCourse(institutionId, courseId);
    final boolean hasCapacity = capacityService.hasCapacity(course);

    if (capacity != null && (capacity == CourseWaitlistCapacity.AVAILABLE) != hasCapacity) {
      return List.of();
    }

    return applicationCourseRepository
        .findAll(filters(institutionId, courseId, search, shift), Sort.by("waitlistNumber"))
        .stream()
        .map(selection -> CourseWaitlistEntryResponse.from(selection, hasCapacity))
        .toList();
  }

  @Transactional(readOnly = true)
  public CourseWaitlistOptionsResponse options(final UUID institutionId, final UUID courseId) {
    final var course = authorizedCourse(institutionId, courseId);

    return new CourseWaitlistOptionsResponse(
        course.getAcademicSpace().getName(),
        course.getTrainingPathId(),
        applicationCourseRepository.findWaitlistPreferredShifts(courseId, institutionId));
  }

  private Course authorizedCourse(final UUID institutionId, final UUID courseId) {
    accessGuard.require(
        PermissionCode.COURSE_WAITLIST_READ, institutionId, ScopedResource.COURSE, courseId);

    return courses
        .findByIdAndInstitution_Id(courseId, institutionId)
        .orElseThrow(CourseNotFoundException::new);
  }

  private Specification<EnrollmentApplicationCourse> filters(
      final UUID institutionId,
      final UUID courseId,
      final @Nullable String search,
      final @Nullable String shift) {
    return (root, query, cb) -> {
      final var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("institution").get("id"), institutionId));
      predicates.add(cb.equal(root.get("course").get("id"), courseId));
      predicates.add(cb.equal(root.get("status"), EnrollmentApplicationCourseStatus.WAITLISTED));

      final var application = root.join("enrollmentApplication");
      final var normalizedSearch = SearchNormalization.normalizeSearch(search);
      if (normalizedSearch != null) {
        final var person = application.join("applicantPerson");
        final var fullName =
            cb.concat(cb.concat(person.get("firstName"), " "), person.get("lastName"));
        final var pattern = SearchNormalization.likeContainsPattern(normalizedSearch);
        predicates.add(
            cb.or(
                cb.like(SearchNormalization.unaccentLower(cb, fullName), pattern, '\\'),
                cb.like(
                    SearchNormalization.unaccentLower(cb, person.get("documentNumber")),
                    pattern,
                    '\\')));
      }

      if (shift != null && !shift.isBlank()) {
        final var preferredShift =
            application.join("preference", JoinType.LEFT).<String>get("preferredShift");
        if ("__without_shift__".equals(shift)) {
          predicates.add(cb.or(cb.isNull(preferredShift), cb.equal(cb.trim(preferredShift), "")));
        } else {
          predicates.add(cb.equal(cb.trim(preferredShift), shift.trim()));
        }
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
