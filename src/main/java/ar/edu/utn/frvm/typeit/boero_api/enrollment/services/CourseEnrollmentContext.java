package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.InstitutionEnrollmentLock;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.InstitutionEnrollmentLockRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.StudentRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentContext {
  private final InstitutionRepository institutions;
  private final InstitutionEnrollmentLockRepository locks;
  private final CourseRepository courses;
  private final CourseClassRepository classes;
  private final StudentRepository students;
  private final Clock clock;
  private final BusinessDateProvider dates;

  @Transactional(propagation = Propagation.MANDATORY)
  public Institution lockInstitution(final UUID institutionId) {
    final var institution =
        institutions
            .findByIdForUpdate(institutionId)
            .orElseThrow(EnrollmentApplicationNotFoundException::new);
    locks
        .findByInstitutionId(institutionId)
        .orElseGet(
            () -> locks.save(InstitutionEnrollmentLock.create(institutionId, clock.instant())));

    return institution;
  }

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public Course activeCourse(final UUID institutionId, final UUID courseId) {
    final var course =
        courses
            .findByIdAndInstitution_Id(courseId, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    if (!course.isActive()) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_NOT_ACTIVE);
    }

    return course;
  }

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public CourseClass courseClass(
      final UUID institutionId, final Course course, final UUID classId) {
    return classes
        .findByIdAndCourseIdAndInstitutionId(classId, course.getId(), institutionId)
        .orElseThrow(
            () -> new EnrollmentValidationException(EnrollmentMessages.COURSE_CLASS_INVALID));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Student getOrCreateStudent(final Institution institution, final Person person) {
    return students
        .findByInstitution_IdAndPerson_Id(institution.getId(), person.getId())
        .orElseGet(
            () ->
                students.save(
                    Student.builder()
                        .institution(institution)
                        .person(person)
                        .fileNumber(
                            String.format(
                                "%d-%05d",
                                dates.today().getYear(), students.nextFileNumberSequenceValue()))
                        .enrollmentDate(dates.today())
                        .build()));
  }

  public static void requireExpectedVersion(final long actual, final @Nullable Long expected) {
    if (expected != null && expected != actual) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_VERSION_STALE);
    }
  }
}
