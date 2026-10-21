package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

public final class AcademicIntegrityViolationTranslator {

  private AcademicIntegrityViolationTranslator() {}

  public static RuntimeException translate(final DataIntegrityViolationException exception) {
    final String constraintName = constraintName(exception);
    if (constraintName == null) {
      return new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP);
    }
    return switch (constraintName) {
      case "academic_years_institution_year_unique",
          "academic_years_current_institution_year_unique" ->
          conflict(AcademicMessages.DUPLICATE_YEAR, "year");
      case "academic_years_active_institution_unique" ->
          conflict(AcademicMessages.ACADEMIC_YEAR_ACTIVE_CONFLICT, "status");
      case "academic_years_year_check" -> validation(AcademicMessages.INVALID_VALUE, "year");
      case "academic_years_dates_check", "academic_years_dates_pair_check" ->
          validation(AcademicMessages.ACADEMIC_YEAR_DATES_INVALID, "endDate");
      case "academic_years_active_dates_check" ->
          validation(AcademicMessages.ACADEMIC_YEAR_DATES_REQUIRED, "endDate");
      case "academic_years_status_check", "study_plans_status_check" ->
          validation(AcademicMessages.INVALID_VALUE, "status");
      case "training_paths_institution_name_unique",
          "training_paths_current_institution_name_unique",
          "study_plans_institution_name_unique",
          "study_plans_current_training_path_name_unique",
          "study_plans_current_training_path_name_version_unique",
          "academic_levels_study_plan_name_unique",
          "academic_spaces_institution_name_type_unique",
          "academic_spaces_current_institution_name_type_unique",
          "academic_spaces_current_institution_name_type_format_unique",
          "instruments_institution_name_unique",
          "instruments_current_institution_name_unique",
          "shifts_institution_name_unique",
          "shifts_current_institution_name_unique" ->
          conflict(AcademicMessages.DUPLICATE_NAME, "name");
      case "training_paths_name_format_check",
          "study_plans_name_format_check",
          "academic_levels_name_format_check",
          "academic_spaces_name_format_check",
          "instruments_name_format_check",
          "shifts_name_format_check" ->
          validation(AcademicMessages.INVALID_NAME_FORMAT, "name");
      case "study_plans_dates_check" ->
          validation(AcademicMessages.STUDY_PLAN_DATES_INVALID, "effectiveTo");
      case "study_plans_active_dates_check" ->
          validation(AcademicMessages.STUDY_PLAN_DATES_INVALID, "effectiveFrom");
      case "study_plans_end_requires_start_check" ->
          validation(AcademicMessages.STUDY_PLAN_START_DATE_REQUIRED, "effectiveFrom");
      case "academic_levels_study_plan_order_unique",
          "study_plan_spaces_plan_level_order_unique",
          "study_plan_spaces_plan_unassigned_order_unique" ->
          conflict(AcademicMessages.DUPLICATE_ORDER, "displayOrder");
      case "academic_levels_display_order_check", "study_plan_spaces_display_order_check" ->
          validation(AcademicMessages.INVALID_DISPLAY_ORDER, "displayOrder");
      case "academic_spaces_type_check" -> validation(AcademicMessages.INVALID_VALUE, "type");
      case "academic_spaces_format_check" -> validation(AcademicMessages.INVALID_VALUE, "format");
      case "courses_institution_space_year_unique" ->
          conflict(AcademicMessages.COURSE_ALREADY_EXISTS, "academicSpaceId");
      case "course_class_days_day_of_week_check",
          "course_class_days_capacity_check",
          "course_class_days_period_duration_check",
          "course_class_schedules_time_range_check" ->
          validation(AcademicMessages.INVALID_VALUE, "classes");
      case "course_individual_slots_schedule_bounds_check" ->
          validation(AcademicMessages.COURSE_PERIOD_DURATION_NOT_DIVISIBLE, "classes");
      case "courses_curriculum_instrument_check" ->
          validation(AcademicMessages.INVALID_RELATIONSHIP, "instrumentId");
      case "academic_spaces_instantiated_shape_check" ->
          conflict(AcademicMessages.ACADEMIC_SPACE_INSTRUMENTAL_HAS_COURSES, "instrumental");
      case "courses_deleted_state_check" -> new InvalidAcademicStateException();
      case "academic_years_deleted_state_check",
          "training_paths_deleted_state_check",
          "study_plans_deleted_state_check",
          "academic_spaces_deleted_state_check",
          "instruments_deleted_state_check",
          "shifts_deleted_state_check" ->
          new InvalidAcademicStateException();
      case "study_plans_previous_version_institution_fk",
          "study_plans_previous_version_self_check",
          "study_plans_version_number_check" ->
          new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP);
      case "study_plans_current_successor_unique" ->
          new AcademicConflictException(AcademicMessages.STUDY_PLAN_VERSION_ALREADY_EXISTS);
      case "study_plan_spaces_requirement_type_check" ->
          validation(AcademicMessages.INVALID_VALUE, "requirementType");
      case "study_plan_spaces_approval_mode_check" ->
          validation(AcademicMessages.INVALID_VALUE, "approvalMode");
      case "study_plan_spaces_plan_level_space_unique",
          "study_plan_spaces_plan_unassigned_space_unique" ->
          conflict(AcademicMessages.DUPLICATE_PLAN_SPACE, "academicSpaceId");
      case "prerequisites_target_required_stage_unique" ->
          conflict(AcademicMessages.DUPLICATE_PREREQUISITE, "requiredStudyPlanSpaceId");
      case "prerequisites_requirement_stage_check" ->
          validation(AcademicMessages.INVALID_VALUE, "requirementStage");
      case "prerequisites_required_condition_check" ->
          validation(AcademicMessages.INVALID_VALUE, "requiredCondition");
      case "study_plans_training_path_institution_fk",
          "study_plan_spaces_plan_institution_fk",
          "study_plan_spaces_space_institution_fk",
          "study_plan_spaces_level_plan_fk",
          "prerequisites_study_plan_fk",
          "prerequisites_target_plan_fk",
          "prerequisites_required_plan_fk",
          "prerequisites_distinct_spaces_check",
          "courses_study_plan_fk",
          "courses_academic_space_fk",
          "courses_academic_year_fk",
          "course_classes_course_fk",
          "course_class_days_class_fk",
          "course_class_schedules_day_fk",
          "course_class_teachers_class_fk",
          "course_class_teachers_person_institution_fk" ->
          new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP);
      default -> new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP);
    };
  }

  private static AcademicConflictException conflict(final String message, final String field) {
    return new AcademicConflictException(message, Map.of(field, message));
  }

  private static AcademicValidationException validation(final String message, final String field) {
    return new AcademicValidationException(message, Map.of(field, message));
  }

  private static @Nullable String constraintName(final DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolation) {
        return constraintViolation.getConstraintName();
      }
      cause = cause.getCause();
    }
    return null;
  }
}
