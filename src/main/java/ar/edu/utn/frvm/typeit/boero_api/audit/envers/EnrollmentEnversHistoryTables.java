package ar.edu.utn.frvm.typeit.boero_api.audit.envers;

import java.util.List;

/** Explicit response allowlist, independent of future Envers mapping additions. */
final class EnrollmentEnversHistoryTables {
  private EnrollmentEnversHistoryTables() {}

  static final List<EnversHistoryTable> TABLES =
      List.of(
          EnversHistoryTable.of(
              "applicant_preferences",
              "Preferencias de la solicitud",
              "applicant_preference_id",
              "allows_image_use:BOOLEAN is_reenrolling:BOOLEAN deleted_at:DATETIME enrollment_application_id:REFERENCE"),
          EnversHistoryTable.of(
              "course_enrollment_schedules",
              "Horario de inscripción",
              "course_enrollment_schedule_id",
              "end_time:TIME start_time:TIME released_at:DATETIME course_class_schedule_id:REFERENCE course_enrollment_id:REFERENCE course_individual_slot_id:REFERENCE institution_id:REFERENCE day_of_week:TEXT"),
          EnversHistoryTable.of(
              "course_enrollments",
              "Inscripción al curso",
              "course_enrollment_id",
              "completed_at:DATETIME enrolled_at:DATETIME withdrawn_at:DATETIME action_authority_person_id:REFERENCE course_class_id:REFERENCE course_id:REFERENCE enrollment_application_course_id:REFERENCE enrollment_application_id:REFERENCE institution_id:REFERENCE student_id:REFERENCE source:TEXT academic_status:TEXT status:TEXT"),
          EnversHistoryTable.of(
              "enrollment_application_courses",
              "Selección de curso",
              "enrollment_application_course_id",
              "submitted_with_capacity:BOOLEAN waitlist_number:NUMBER requested_at:DATETIME resolved_at:DATETIME waitlisted_at:DATETIME course_id:REFERENCE enrollment_application_id:REFERENCE enrollment_period_id:REFERENCE institution_id:REFERENCE preferred_teacher_id:REFERENCE resolved_by_person_id:REFERENCE status:TEXT resolution_reason_code:TEXT waitlist_reason:TEXT"),
          EnversHistoryTable.of(
              "enrollment_application_spaces",
              "Selección de espacio",
              "enrollment_application_space_id",
              "enrollment_application_id:REFERENCE instrument_id:REFERENCE study_plan_space_id:REFERENCE"),
          EnversHistoryTable.of(
              "enrollment_applications",
              "Solicitud de inscripción",
              "enrollment_application_id",
              "deleted_at:DATETIME resolved_at:DATETIME academic_year_id:REFERENCE applicant_person_id:REFERENCE enrollment_period_id:REFERENCE institution_id:REFERENCE resolved_by_person_id:REFERENCE study_plan_id:REFERENCE submitted_by_person_id:REFERENCE training_path_id:REFERENCE status:TEXT"),
          EnversHistoryTable.of(
              "enrollment_attachments",
              "Adjunto de inscripción",
              "enrollment_attachment_id",
              "deleted_at:DATETIME file_size:NUMBER reviewed_at:DATETIME enrollment_application_id:REFERENCE requirement_id:REFERENCE reviewed_by:REFERENCE uploaded_by:REFERENCE content_type:TEXT review_status:TEXT reviewer_type:TEXT uploader_type:TEXT version_status:TEXT"),
          EnversHistoryTable.of(
              "enrollment_document_requests",
              "Solicitud documental",
              "id",
              "created_at:DATETIME actor_id:REFERENCE application_id:REFERENCE institution_id:REFERENCE account_type:TEXT"),
          EnversHistoryTable.of(
              "enrollment_document_requirements",
              "Requisito documental",
              "id",
              "active:BOOLEAN display_order:NUMBER assignment_revision:NUMBER definition_revision:NUMBER application_id:REFERENCE document_id:REFERENCE institution_id:REFERENCE request_id:REFERENCE source_requirement_id:REFERENCE name:TEXT level:TEXT origin:TEXT allowed_formats:JSON"),
          EnversHistoryTable.of(
              "enrollment_period_offering_levels",
              "Nivel habilitado",
              "offering_level_id",
              "academic_level_id:REFERENCE offering_id:REFERENCE"),
          EnversHistoryTable.of(
              "enrollment_period_offerings",
              "Oferta habilitada",
              "offering_id",
              "enrollment_period_id:REFERENCE study_plan_id:REFERENCE"),
          EnversHistoryTable.of(
              "enrollment_periods",
              "Período de inscripción",
              "enrollment_period_id",
              "scope_configured:BOOLEAN deleted_at:DATETIME end_date:DATETIME start_date:DATETIME academic_year_id:REFERENCE institution_id:REFERENCE status:TEXT name:TEXT"));
}
