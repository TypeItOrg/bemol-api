package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.InstitutionEnrollmentLock;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.InstitutionEnrollmentLockRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.time.Clock;
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
public class CourseEnrollmentGradeStore {

  private final InstitutionRepository institutionRepository;
  private final InstitutionEnrollmentLockRepository institutionEnrollmentLockRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseClassRepository courseClassRepository;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public Institution lockInstitution(final UUID institutionId) {
    final Institution institution =
        institutionRepository
            .findByIdForUpdate(institutionId)
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND));
    institutionEnrollmentLockRepository
        .findByInstitutionId(institutionId)
        .orElseGet(
            () ->
                institutionEnrollmentLockRepository.save(
                    InstitutionEnrollmentLock.create(institutionId, clock.instant())));

    return institution;
  }

  @Transactional(propagation = Propagation.MANDATORY)
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

  @Transactional(propagation = Propagation.MANDATORY)
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

  public RuntimeException translateIntegrityViolation(
      final DataIntegrityViolationException exception) {
    final String constraint = constraintName(exception);

    if ("course_enrollment_grades_enrollment_evaluation_unique".equals(constraint)) {
      return new EnrollmentValidationException(GradeMessages.EVALUATION_DUPLICATE);
    }

    if (constraint != null && constraint.contains("course_enrollment_grades_value_range_check")) {
      return new EnrollmentValidationException(GradeMessages.VALUE_OUT_OF_RANGE);
    }

    if (constraint != null
        && constraint.contains("course_enrollment_grades_published_value_range_check")) {
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
