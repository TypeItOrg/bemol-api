package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

public final class EnrollmentIntegrityViolationTranslator {
  private EnrollmentIntegrityViolationTranslator() {}

  public static RuntimeException draft(final DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (!(cause instanceof ConstraintViolationException violation)
          || violation.getConstraintName() == null) {
        continue;
      }
      switch (violation.getConstraintName()) {
        case "enrollment_application_academic_year_check":
          return validation(EnrollmentMessages.APPLICATION_ACADEMIC_YEAR_INVALID);
        case "enrollment_course_period_scope_check":
          return validation(EnrollmentMessages.COURSE_OUTSIDE_PERIOD);
        case "enrollment_application_courses_active_applicant_course_unique":
          return validation(EnrollmentMessages.COURSE_ALREADY_REQUESTED);
        case "enrollment_application_courses_context_check":
          return validation(EnrollmentMessages.COURSE_NOT_ACTIVE);
        case "enrollment_apps_applicant_open_path_unique",
        "enrollment_apps_applicant_open_path_period_unique",
        "enrollment_apps_applicant_active_path_unique",
        "enrollment_apps_applicant_active_draft_unique",
        "enrollment_apps_applicant_open_path_year_unique":
          return new ActiveEnrollmentApplicationExistsException();
        default:
          break;
      }
    }

    return exception;
  }

  public static RuntimeException submission(final DataIntegrityViolationException exception) {
    return hasConstraint(exception, "enrollment_application_academic_requirements_check")
        ? validation(EnrollmentMessages.ACADEMIC_REQUIREMENTS_PENDING)
        : exception;
  }

  public static RuntimeException attachment(final DataIntegrityViolationException exception) {
    return hasConstraint(exception, "enrollment_attachments_current_requirement_unique")
        ? validation(EnrollmentMessages.ATTACHMENT_TYPE_CONFLICT)
        : exception;
  }

  public static RuntimeException courseEnrollment(final DataIntegrityViolationException exception) {
    final var name = firstConstraintName(exception);
    if (name == null) {
      return exception;
    }

    return switch (name) {
      case "course_enrollment_academic_requirements_check" ->
          validation(EnrollmentMessages.ACADEMIC_REQUIREMENTS_PENDING);
      case "course_enrollment_schedules_active_slot_unique",
          "course_enrollment_schedules_capacity_check" ->
          validation(EnrollmentMessages.COURSE_CAPACITY_EXCEEDED);
      case "course_enrollments_active_student_course_unique",
          "course_enrollments_application_course_unique" ->
          validation(EnrollmentMessages.COURSE_ALREADY_ENROLLED);
      case "course_enrollment_schedules_context_check",
          "course_enrollment_schedules_active_day_unique",
          "course_enrollment_schedules_group_assignment_unique",
          "course_enrollment_schedules_matching_slot_fk" ->
          validation(EnrollmentMessages.COURSE_ASSIGNMENT_INVALID);
      case "course_enrollments_origin_check" -> validation(EnrollmentMessages.PARENT_NOT_APPROVED);
      default -> exception;
    };
  }

  private static EnrollmentValidationException validation(final String message) {
    return new EnrollmentValidationException(message);
  }

  private static boolean hasConstraint(final Throwable exception, final String name) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && name.equals(violation.getConstraintName())) {
        return true;
      }
    }

    return false;
  }

  private static @Nullable String firstConstraintName(final Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return violation.getConstraintName();
      }
    }

    return null;
  }
}
