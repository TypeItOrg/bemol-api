package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeSupport {

  private final EnrollmentInstitutionLock institutionLock;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseClassRepository courseClassRepository;
  private final PersonRepository personRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockInstitution(final UUID institutionId) {
    institutionLock.lock(institutionId);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public CourseEnrollment findEnrollment(final UUID institutionId, final UUID enrollmentId) {
    return courseEnrollmentRepository
        .findByIdAndInstitutionId(enrollmentId, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollment findEnrollmentForUpdate(
      final UUID institutionId, final UUID enrollmentId) {
    return courseEnrollmentRepository
        .findByIdAndInstitutionIdForUpdate(enrollmentId, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollmentGrade findGradeForUpdate(final UUID institutionId, final UUID gradeId) {
    return gradeRepository
        .findByIdAndInstitutionIdForUpdate(gradeId, institutionId)
        .orElseThrow(() -> new EnrollmentValidationException(GradeMessages.GRADE_NOT_FOUND));
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public CourseClass findClassReadOnly(final UUID institutionId, final UUID classId) {
    return courseClassRepository
        .findByIdAndInstitutionId(classId, institutionId)
        .orElseThrow(
            () -> new EnrollmentValidationException(EnrollmentMessages.COURSE_CLASS_INVALID));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseClass findClassForUpdate(final UUID institutionId, final UUID classId) {
    return courseClassRepository
        .findByIdAndInstitutionIdForUpdate(classId, institutionId)
        .orElseThrow(
            () -> new EnrollmentValidationException(EnrollmentMessages.COURSE_CLASS_INVALID));
  }

  public void requireEnrolled(final CourseEnrollment enrollment) {
    if (enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED) {
      throw new EnrollmentValidationException(GradeMessages.ENROLLMENT_NOT_ENROLLED);
    }
  }

  public void requireGradeBelongsToEnrollment(
      final CourseEnrollmentGrade grade, final CourseEnrollment enrollment) {
    if (!grade.getCourseEnrollment().getId().equals(enrollment.getId())) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_ENROLLMENT_MISMATCH);
    }
  }

  public void ensureExpectedVersion(
      final long actualVersion, final @Nullable Long expectedVersion) {
    if (expectedVersion != null && expectedVersion != actualVersion) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_VERSION_STALE);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeResponse> toManagementResponses(
      final List<CourseEnrollmentGrade> grades) {
    final Set<UUID> personIds = new HashSet<>();

    for (final CourseEnrollmentGrade grade : grades) {
      if (grade.getCreatedByPersonId() != null) {
        personIds.add(grade.getCreatedByPersonId());
      }
      if (grade.getUpdatedByPersonId() != null) {
        personIds.add(grade.getUpdatedByPersonId());
      }
      if (grade.getPublishedByPersonId() != null) {
        personIds.add(grade.getPublishedByPersonId());
      }
    }

    final Map<UUID, Person> people = resolvePeople(personIds);
    return grades.stream().map(grade -> CourseEnrollmentGradeResponse.from(grade, people)).toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Map<UUID, Person> resolvePeople(final Collection<UUID> personIds) {
    if (personIds.isEmpty()) {
      return Map.of();
    }

    final Map<UUID, Person> people = new HashMap<>();

    for (final Person person : personRepository.findAllById(personIds)) {
      if (person.getId() != null) {
        people.put(Objects.requireNonNull(person.getId()), person);
      }
    }

    return people;
  }

  public RuntimeException translateIntegrityViolation(
      final DataIntegrityViolationException exception) {
    final String constraint = constraintName(exception);

    if ("course_enrollment_grades_enrollment_evaluation_unique".equals(constraint)) {
      return new EnrollmentValidationException(GradeMessages.EVALUATION_DUPLICATE);
    }

    if (constraint != null
        && (constraint.contains("course_enrollment_grades_value_range_check")
            || constraint.contains("course_enrollment_grades_published_value_range_check"))) {
      return new EnrollmentValidationException(GradeMessages.VALUE_OUT_OF_RANGE);
    }

    return exception;
  }

  private @Nullable String constraintName(final DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return violation.getConstraintName();
      }
    }

    return null;
  }
}
