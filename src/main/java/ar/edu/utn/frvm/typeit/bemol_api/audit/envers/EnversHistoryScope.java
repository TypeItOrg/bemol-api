package ar.edu.utn.frvm.typeit.bemol_api.audit.envers;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import java.util.List;
import org.jspecify.annotations.Nullable;

record EnversHistoryScope(
    String table, @Nullable String parent, String column, @Nullable String parentColumn) {
  private static EnversHistoryScope root(final String table) {
    return new EnversHistoryScope(table, null, "", null);
  }

  private static EnversHistoryScope child(
      final String table, final String parent, final String column) {
    return new EnversHistoryScope(table, parent, column, null);
  }

  static List<EnversHistoryScope> forEntity(final AuditEntityType entity) {
    return switch (entity) {
      case SHIFT -> List.of(root("shifts"));
      case INSTITUTION -> List.of(root("institutions"));
      case ACADEMIC_YEAR -> List.of(root("academic_years"));
      case ACADEMIC_SPACE -> List.of(root("academic_spaces"));
      case INSTRUMENT -> List.of(root("instruments"));
      case ACADEMIC_LEVEL -> List.of(root("academic_levels"));
      case PREREQUISITE -> List.of(root("prerequisites"));
      case PASSKEY -> List.of(root("passkey_credentials"));
      case ENROLLMENT_ATTACHMENT -> List.of(root("enrollment_attachments"));
      case PERSON_GUARDIAN_ATTACHMENT -> List.of(root("person_guardian_attachments"));
      case USER -> List.of(root("users"));
      case PLATFORM_ACCOUNT ->
          List.of(
              root("platform_accounts"),
              child("platform_account_roles", "platform_accounts", "platform_account_id"));
      case ROLE -> List.of(root("roles"), child("role_permissions", "roles", "role_id"));
      case PERSON ->
          List.of(
              root("people"),
              child("users", "people", "person_id"),
              child("person_role_assignments", "people", "person_id"),
              child(
                  "person_role_assignment_training_paths",
                  "person_role_assignments",
                  "person_role_assignment_id"),
              child("students", "people", "person_id"),
              child("person_access_invitations", "people", "person_id"),
              child("passkey_credentials", "users", "user_id"));
      case PERSON_ACCESS_INVITATION ->
          List.of(
              root("person_access_invitations"),
              new EnversHistoryScope(
                  "users", "person_access_invitations", "person_id", "person_id"));
      case PERSON_GUARDIAN ->
          List.of(
              root("person_guardians"),
              child("person_guardian_attachments", "person_guardians", "person_guardian_id"),
              new EnversHistoryScope(
                  "users", "person_guardians", "person_id", "dependent_person_id"));
      case COURSE ->
          List.of(
              root("courses"),
              child("course_classes", "courses", "course_id"),
              child("course_class_teachers", "course_classes", "course_class_id"),
              child("course_class_days", "course_classes", "course_class_id"),
              child("course_class_schedules", "course_class_days", "course_class_day_id"));
      case STUDY_PLAN ->
          List.of(
              root("study_plans"),
              child("academic_levels", "study_plans", "study_plan_id"),
              child("study_plan_spaces", "study_plans", "study_plan_id"),
              child("study_plan_space_instruments", "study_plan_spaces", "study_plan_space_id"),
              child("prerequisites", "study_plans", "study_plan_id"));
      case STUDY_PLAN_SPACE ->
          List.of(
              root("study_plan_spaces"),
              child("study_plan_space_instruments", "study_plan_spaces", "study_plan_space_id"));
      case TRAINING_PATH ->
          List.of(
              root("training_paths"),
              child("training_path_document_requirements", "training_paths", "training_path_id"));
      case DOCUMENT_DEFINITION ->
          List.of(
              root("document_definitions"),
              child("training_path_document_requirements", "document_definitions", "document_id"));
      case ENROLLMENT_PERIOD ->
          List.of(
              root("enrollment_periods"),
              child("enrollment_period_offerings", "enrollment_periods", "enrollment_period_id"),
              child(
                  "enrollment_period_offering_levels",
                  "enrollment_period_offerings",
                  "offering_id"),
              child(
                  "enrollment_application_courses", "enrollment_periods", "enrollment_period_id"));
      case ENROLLMENT_APPLICATION ->
          List.of(
              root("enrollment_applications"),
              new EnversHistoryScope(
                  "students", "enrollment_applications", "person_id", "applicant_person_id"),
              child(
                  "enrollment_application_spaces",
                  "enrollment_applications",
                  "enrollment_application_id"),
              child(
                  "enrollment_application_courses",
                  "enrollment_applications",
                  "enrollment_application_id"),
              child("enrollment_document_requests", "enrollment_applications", "application_id"),
              child(
                  "enrollment_document_requirements", "enrollment_applications", "application_id"),
              child(
                  "applicant_preferences", "enrollment_applications", "enrollment_application_id"),
              child(
                  "enrollment_attachments",
                  "enrollment_applications",
                  "enrollment_application_id"));
      case COURSE_ENROLLMENT ->
          List.of(
              root("course_enrollments"),
              new EnversHistoryScope("students", "course_enrollments", "student_id", "student_id"),
              child("course_enrollment_schedules", "course_enrollments", "course_enrollment_id"));
      case EMAIL -> List.of();
    };
  }
}
