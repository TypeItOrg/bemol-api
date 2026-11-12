package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

public final class EnrollmentIntegrityViolationTranslator {
  private EnrollmentIntegrityViolationTranslator() {}

  public static @Nullable EnrollmentFormException form(final String constraint) {
    final var conflict =
        switch (constraint) {
          case "enrollment_form_versions_draft_unique" -> EnrollmentFormMessages.DRAFT_EXISTS;
          case "enrollment_form_versions_number_unique" -> EnrollmentFormMessages.REVISION_CONFLICT;
          case "enrollment_form_versions_assigned_check" -> EnrollmentFormMessages.VERSION_IN_USE;
          case "enrollment_form_versions_immutable_check",
              "enrollment_form_versions_identity_check",
              "enrollment_form_versions_transition_check" ->
              EnrollmentFormMessages.VERSION_IMMUTABLE;
          case "enrollment_form_assignments_published_check",
              "enrollment_application_form_published_check" ->
              EnrollmentFormMessages.VERSION_NOT_PUBLISHED;
          case "enrollment_form_assignments_context_unique" ->
              EnrollmentFormMessages.ASSIGNMENT_CONFLICT;
          case "enrollment_forms_identity_check", "enrollment_form_assignments_identity_check" ->
              EnrollmentFormMessages.INSTITUTION_MISMATCH;
          case "enrollment_application_form_immutable_check" ->
              EnrollmentFormMessages.APPLICATION_FORM_IMMUTABLE;
          case "enrollment_application_form_context_check" ->
              EnrollmentFormMessages.APPLICATION_FORM_CONTEXT_IMMUTABLE;
          case "enrollment_application_answers_frozen_check" ->
              EnrollmentFormMessages.ANSWERS_IMMUTABLE;
          default -> null;
        };
    if (conflict != null) {
      return new EnrollmentFormException(ErrorCategory.CONFLICT, conflict);
    }

    final var invalid =
        switch (constraint) {
          case "enrollment_forms_name_check" -> EnrollmentFormMessages.NAME_INVALID;
          case "enrollment_form_versions_number_check" ->
              EnrollmentFormMessages.VERSION_NUMBER_INVALID;
          case "enrollment_form_versions_definition_check" ->
              EnrollmentFormMessages.DEFINITION_INVALID;
          case "enrollment_form_versions_state_check" ->
              EnrollmentFormMessages.TRANSITION_DATE_INVALID;
          case "enrollment_forms_revision_check",
              "enrollment_form_versions_revision_check",
              "enrollment_form_assignments_revision_check",
              "enrollment_application_draft_revision_check" ->
              EnrollmentFormMessages.REVISION_CONFLICT;
          case "enrollment_form_versions_form_tenant_fk",
              "enrollment_form_assignments_path_tenant_fk",
              "enrollment_form_assignments_year_tenant_fk",
              "enrollment_form_assignments_version_tenant_fk",
              "enrollment_application_form_version_tenant_fk" ->
              EnrollmentFormMessages.INSTITUTION_MISMATCH;
          case "enrollment_application_custom_answers_check",
              "enrollment_application_base_answers_check" ->
              EnrollmentFormMessages.ANSWERS_INVALID;
          default -> null;
        };
    return invalid == null
        ? null
        : new EnrollmentFormException(ErrorCategory.INVALID_INPUT, invalid);
  }

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
