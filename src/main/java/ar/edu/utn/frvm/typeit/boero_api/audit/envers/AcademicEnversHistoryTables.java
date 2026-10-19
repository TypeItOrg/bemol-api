package ar.edu.utn.frvm.typeit.boero_api.audit.envers;

import java.util.List;

/** Explicit response allowlist, independent of future Envers mapping additions. */
final class AcademicEnversHistoryTables {
  private AcademicEnversHistoryTables() {}

  static final List<EnversHistoryTable> TABLES =
      List.of(
          EnversHistoryTable.of(
              "shifts",
              "Turno",
              "shift_id",
              "institution_id:REFERENCE name:TEXT active:BOOLEAN deleted_at:DATETIME"),
          EnversHistoryTable.of(
              "courses",
              "Curso",
              "course_id",
              "institution_id:REFERENCE study_plan_space_id:REFERENCE instrument_id:REFERENCE academic_year_id:REFERENCE status:TEXT deleted_at:DATETIME"),
          EnversHistoryTable.of(
              "course_classes",
              "Clase",
              "course_class_id",
              "institution_id:REFERENCE course_id:REFERENCE class_number:NUMBER"),
          EnversHistoryTable.of(
              "course_class_teachers",
              "Docente de clase",
              "course_class_teacher_id",
              "institution_id:REFERENCE course_class_id:REFERENCE person_id:REFERENCE"),
          EnversHistoryTable.of(
              "course_class_days",
              "Día de clase",
              "course_class_day_id",
              "institution_id:REFERENCE course_class_id:REFERENCE day_of_week:TEXT capacity:NUMBER period_duration_minutes:NUMBER"),
          EnversHistoryTable.of(
              "course_class_schedules",
              "Horario de clase",
              "course_class_schedule_id",
              "institution_id:REFERENCE course_class_day_id:REFERENCE start_time:TIME end_time:TIME"),
          EnversHistoryTable.of(
              "academic_levels",
              "Nivel académico",
              "academic_level_id",
              "display_order:NUMBER study_plan_id:REFERENCE name:TEXT"),
          EnversHistoryTable.of(
              "academic_spaces",
              "Espacio académico",
              "academic_space_id",
              "active:BOOLEAN instrumental:BOOLEAN deleted_at:DATETIME institution_id:REFERENCE format:TEXT type:TEXT name:TEXT"),
          EnversHistoryTable.of(
              "academic_years",
              "Ciclo lectivo",
              "academic_year_id",
              "end_date:DATE start_date:DATE year:NUMBER deleted_at:DATETIME institution_id:REFERENCE status:TEXT"),
          EnversHistoryTable.of(
              "document_definitions",
              "Documento",
              "id",
              "active:BOOLEAN institution_id:REFERENCE name:TEXT allowed_formats:JSON"),
          EnversHistoryTable.of(
              "instruments",
              "Instrumento",
              "instrument_id",
              "active:BOOLEAN deleted_at:DATETIME institution_id:REFERENCE name:TEXT"),
          EnversHistoryTable.of(
              "prerequisites",
              "Correlatividad",
              "prerequisite_id",
              "required_study_plan_space_id:REFERENCE study_plan_id:REFERENCE target_study_plan_space_id:REFERENCE required_condition:TEXT requirement_stage:TEXT"),
          EnversHistoryTable.of(
              "study_plan_space_instruments",
              "Instrumento del espacio",
              "study_plan_space_instrument_id",
              "institution_id:REFERENCE instrument_id:REFERENCE study_plan_space_id:REFERENCE"),
          EnversHistoryTable.of(
              "study_plan_spaces",
              "Espacio del plan",
              "study_plan_space_id",
              "display_order:NUMBER academic_level_id:REFERENCE academic_space_id:REFERENCE institution_id:REFERENCE study_plan_id:REFERENCE requirement_type:TEXT approval_mode:TEXT"),
          EnversHistoryTable.of(
              "study_plans",
              "Plan de estudio",
              "study_plan_id",
              "effective_from:DATE effective_to:DATE version_number:NUMBER deleted_at:DATETIME institution_id:REFERENCE previous_version_id:REFERENCE training_path_id:REFERENCE status:TEXT name:TEXT"),
          EnversHistoryTable.of(
              "training_path_document_requirements",
              "Asignación documental",
              "id",
              "active:BOOLEAN display_order:NUMBER document_id:REFERENCE institution_id:REFERENCE training_path_id:REFERENCE level:TEXT"),
          EnversHistoryTable.of(
              "training_paths",
              "Trayecto formativo",
              "training_path_id",
              "active:BOOLEAN deleted_at:DATETIME institution_id:REFERENCE name:TEXT"));
}
