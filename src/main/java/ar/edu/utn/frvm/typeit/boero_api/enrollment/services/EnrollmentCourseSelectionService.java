package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseSelectionDto;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.StudentRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentCourseSelectionService {
  private final CourseRepository courses;
  private final CourseClassTeacherRepository teachers;
  private final PersonRepository people;
  private final EnrollmentApplicationCourseRepository selections;
  private final CourseEnrollmentRepository enrollments;
  private final StudentRepository students;
  private final EnrollmentApplicationPeriodService periods;
  private final AcademicEligibilityService eligibility;

  @Transactional(propagation = Propagation.MANDATORY)
  public void update(
      final EnrollmentApplication application, final List<CourseSelectionDto> requests) {
    final var requestedIds = requireDistinctCourses(requests);

    final var existingByCourse =
        application.getCourseSelections().stream()
            .collect(
                Collectors.toMap(selection -> selection.getCourse().getId(), Function.identity()));
    application
        .getCourseSelections()
        .removeIf(selection -> !requestedIds.contains(selection.getCourse().getId()));

    for (final var request : requests) {
      final var course = requireCourse(application, request.courseId());
      final var existing = existingByCourse.get(course.getId());
      final var period =
          periods.resolveCoursePeriod(
              application, course, existing == null ? null : existing.getEnrollmentPeriod());
      requireAvailable(application, course.getId());
      eligibility.requireEligible(
          application.getInstitution().getId(), application.getApplicantPerson().getId(), course);
      final var teacher = preferredTeacher(application, course, request.preferredTeacherId());

      if (existing != null) {
        existing.assignPeriod(period);
        existing.changePreferredTeacher(teacher);
      } else {
        final var selection =
            EnrollmentApplicationCourse.create(
                application.getInstitution(), application, course, teacher);
        selection.assignPeriod(period);
        application.addCourseSelection(selection);
      }
    }
  }

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public boolean changed(
      final EnrollmentApplication application, final List<CourseSelectionDto> requests) {
    final var requestedIds = requireDistinctCourses(requests);
    if (application.getCourseSelections().size() != requests.size()) {
      return true;
    }

    final Map<UUID, UUID> existingTeachers = new HashMap<>();
    for (final var selection : application.getCourseSelections()) {
      existingTeachers.put(
          selection.getCourse().getId(),
          selection.getPreferredTeacher() == null ? null : selection.getPreferredTeacher().getId());
    }
    if (!existingTeachers.keySet().equals(requestedIds)) {
      return true;
    }

    return requests.stream()
        .anyMatch(
            request ->
                !Objects.equals(
                    existingTeachers.get(request.courseId()), request.preferredTeacherId()));
  }

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public void requireAvailable(final EnrollmentApplication application, final UUID courseId) {
    if (selections.existsBlockingCourseSelectionExcludingApplication(
        application.getInstitution().getId(),
        courseId,
        application.getId(),
        application.getApplicantPerson().getId(),
        List.of(
            EnrollmentApplicationCourseStatus.PENDING,
            EnrollmentApplicationCourseStatus.WAITLISTED))) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ALREADY_REQUESTED);
    }

    final var student =
        students.findByInstitution_IdAndPerson_Id(
            application.getInstitution().getId(), application.getApplicantPerson().getId());
    if (student.isPresent()
        && enrollments
            .findByStudentAndCourseAndStatus(
                application.getInstitution().getId(),
                student.get().getId(),
                courseId,
                CourseEnrollmentStatus.ENROLLED)
            .isPresent()) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ALREADY_ENROLLED);
    }
  }

  private Set<UUID> requireDistinctCourses(final List<CourseSelectionDto> requests) {
    final var ids =
        requests.stream().map(request -> request.courseId()).collect(Collectors.toSet());
    if (ids.size() != requests.size()) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.ENROLLMENT_APPLICATION_SPACES_DUPLICATED);
    }

    return ids;
  }

  private Course requireCourse(final EnrollmentApplication application, final UUID courseId) {
    final var course =
        courses
            .findByIdAndInstitution_Id(courseId, application.getInstitution().getId())
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.SPACE_ID_NOT_FOUND + courseId));
    if (!course.isActive()
        || course.getDeletedAt() != null
        || course.getStudyPlanSpace() == null
        || !course
            .getStudyPlanSpace()
            .getStudyPlan()
            .getTrainingPath()
            .getId()
            .equals(requireNonNull(application.getTrainingPath()).getId())) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.ENROLLMENT_APPLICATION_TRAINING_PATH_INVALID);
    }

    return course;
  }

  private @Nullable Person preferredTeacher(
      final EnrollmentApplication application,
      final Course course,
      final @Nullable UUID teacherId) {
    if (teacherId == null) {
      return null;
    }

    final var teacher =
        people
            .findByIdAndInstitution_Id(teacherId, application.getInstitution().getId())
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.PERSON_ID_NOT_FOUND + teacherId));
    if (!teachers.existsByCourseClass_Course_IdAndPerson_Id(course.getId(), teacher.getId())) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.ENROLLMENT_APPLICATION_TEACHER_INVALID);
    }

    return teacher;
  }
}
