package ar.edu.utn.frvm.typeit.bemol_api.academic.audit;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperationsProvider;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import java.util.List;
import org.springframework.stereotype.Component;

public enum AcademicAuditAction implements AuditOperation {
  COURSE_CREATED(Modules.COURSES, "Curso creado"),
  COURSE_CLASSES_CHANGED(Modules.COURSES, "Clases de curso actualizadas"),
  COURSE_STATUS_CHANGED(Modules.COURSES, "Estado de curso modificado"),
  COURSE_DELETED(Modules.COURSES, "Curso eliminado"),
  COURSE_RESTORED(Modules.COURSES, "Curso restaurado"),
  STUDY_PLAN_CREATED(Modules.STUDY_PLANS, "Plan de estudio creado"),
  STUDY_PLAN_UPDATED(Modules.STUDY_PLANS, "Plan de estudio actualizado"),
  STUDY_PLAN_STATUS_CHANGED(Modules.STUDY_PLANS, "Estado de plan de estudio modificado"),
  STUDY_PLAN_VERSION_CREATED(Modules.STUDY_PLANS, "Versión de plan de estudio creada"),
  STUDY_PLAN_DELETED(Modules.STUDY_PLANS, "Plan de estudio eliminado"),
  STUDY_PLAN_RESTORED(Modules.STUDY_PLANS, "Plan de estudio restaurado"),
  STUDY_PLAN_SPACE_CREATED(Modules.STUDY_PLANS, "Espacio de plan de estudio agregado"),
  STUDY_PLAN_LEVEL_CREATED(Modules.STUDY_PLANS, "Nivel de plan de estudio agregado"),
  STUDY_PLAN_LEVEL_UPDATED(Modules.STUDY_PLANS, "Nivel de plan de estudio actualizado"),
  STUDY_PLAN_LEVEL_DELETED(Modules.STUDY_PLANS, "Nivel de plan de estudio eliminado"),
  STUDY_PLAN_SPACE_UPDATED(Modules.STUDY_PLANS, "Espacio de plan de estudio actualizado"),
  STUDY_PLAN_SPACE_DELETED(Modules.STUDY_PLANS, "Espacio de plan de estudio eliminado"),
  STUDY_PLAN_PREREQUISITE_CREATED(
      Modules.STUDY_PLANS, "Correlatividad de plan de estudio agregada"),
  STUDY_PLAN_PREREQUISITE_UPDATED(
      Modules.STUDY_PLANS, "Correlatividad de plan de estudio actualizada"),
  STUDY_PLAN_PREREQUISITE_DELETED(
      Modules.STUDY_PLANS, "Correlatividad de plan de estudio eliminada"),
  DOCUMENT_DEFINITION_CREATED(Modules.DOCUMENTATION, "Documento del catálogo creado"),
  DOCUMENT_DEFINITION_UPDATED(Modules.DOCUMENTATION, "Documento del catálogo actualizado"),
  DOCUMENT_DEFINITION_TRANSFERRED(Modules.DOCUMENTATION, "Documento del catálogo transferido"),
  DOCUMENT_REQUIREMENTS_CHANGED(Modules.DOCUMENTATION, "Requisitos documentales modificados"),
  TRAINING_PATH_CREATED(Modules.TRAINING_PATHS, "Trayecto formativo creado"),
  TRAINING_PATH_UPDATED(Modules.TRAINING_PATHS, "Trayecto formativo actualizado"),
  TRAINING_PATH_STATUS_CHANGED(Modules.TRAINING_PATHS, "Trayecto formativo con estado modificado"),
  TRAINING_PATH_DELETED(Modules.TRAINING_PATHS, "Trayecto formativo eliminado"),
  TRAINING_PATH_RESTORED(Modules.TRAINING_PATHS, "Trayecto formativo restaurado"),
  ACADEMIC_YEAR_CREATED(Modules.ACADEMIC_YEARS, "Ciclo lectivo creado"),
  ACADEMIC_YEAR_UPDATED(Modules.ACADEMIC_YEARS, "Ciclo lectivo actualizado"),
  ACADEMIC_YEAR_STATUS_CHANGED(Modules.ACADEMIC_YEARS, "Ciclo lectivo con estado modificado"),
  ACADEMIC_YEAR_DELETED(Modules.ACADEMIC_YEARS, "Ciclo lectivo eliminado"),
  ACADEMIC_YEAR_RESTORED(Modules.ACADEMIC_YEARS, "Ciclo lectivo restaurado"),
  ACADEMIC_SPACE_CREATED(Modules.ACADEMIC_SPACES, "Espacio académico creado"),
  ACADEMIC_SPACE_UPDATED(Modules.ACADEMIC_SPACES, "Espacio académico actualizado"),
  ACADEMIC_SPACE_STATUS_CHANGED(Modules.ACADEMIC_SPACES, "Espacio académico con estado modificado"),
  ACADEMIC_SPACE_DELETED(Modules.ACADEMIC_SPACES, "Espacio académico eliminado"),
  ACADEMIC_SPACE_RESTORED(Modules.ACADEMIC_SPACES, "Espacio académico restaurado"),
  INSTRUMENT_CREATED(Modules.INSTRUMENTS, "Instrumento creado"),
  INSTRUMENT_UPDATED(Modules.INSTRUMENTS, "Instrumento actualizado"),
  INSTRUMENT_STATUS_CHANGED(Modules.INSTRUMENTS, "Instrumento con estado modificado"),
  INSTRUMENT_DELETED(Modules.INSTRUMENTS, "Instrumento eliminado"),
  INSTRUMENT_RESTORED(Modules.INSTRUMENTS, "Instrumento restaurado"),
  SHIFT_CREATED(Modules.SHIFTS, "Turno creado"),
  SHIFT_UPDATED(Modules.SHIFTS, "Turno actualizado"),
  SHIFT_STATUS_CHANGED(Modules.SHIFTS, "Turno con estado modificado"),
  SHIFT_DELETED(Modules.SHIFTS, "Turno eliminado"),
  SHIFT_RESTORED(Modules.SHIFTS, "Turno restaurado");

  private final AuditModuleDefinition module;
  private final String label;

  AcademicAuditAction(final AuditModuleDefinition module, final String label) {
    this.module = module;
    this.label = label;
  }

  @Override
  public AuditModuleDefinition getModule() {
    return module;
  }

  @Override
  public String getLabel() {
    return label;
  }

  private static final class Modules {
    static final AuditModuleDefinition COURSES = new AuditModuleDefinition("COURSES", "Cursos");
    static final AuditModuleDefinition STUDY_PLANS =
        new AuditModuleDefinition("STUDY_PLANS", "Planes de estudio");
    static final AuditModuleDefinition DOCUMENTATION =
        new AuditModuleDefinition("DOCUMENTATION", "Documentación");
    static final AuditModuleDefinition TRAINING_PATHS =
        new AuditModuleDefinition("TRAINING_PATHS", "Trayectos formativos");
    static final AuditModuleDefinition ACADEMIC_YEARS =
        new AuditModuleDefinition("ACADEMIC_YEARS", "Ciclos lectivos");
    static final AuditModuleDefinition ACADEMIC_SPACES =
        new AuditModuleDefinition("ACADEMIC_SPACES", "Espacios académicos");
    static final AuditModuleDefinition INSTRUMENTS =
        new AuditModuleDefinition("INSTRUMENTS", "Instrumentos");
    static final AuditModuleDefinition SHIFTS = new AuditModuleDefinition("SHIFTS", "Turnos");
  }

  @Component
  public static class Provider implements AuditOperationsProvider {
    @Override
    public List<AcademicAuditAction> operations() {
      return List.of(values());
    }

    @Override
    public List<AuditResource> resources() {
      return List.of(
          TrainingPath.AUDIT_TYPE,
          AcademicYear.AUDIT_TYPE,
          AcademicSpace.AUDIT_TYPE,
          Instrument.AUDIT_TYPE,
          Shift.AUDIT_TYPE,
          Course.AUDIT_TYPE,
          StudyPlan.AUDIT_TYPE,
          AcademicLevel.AUDIT_TYPE,
          StudyPlanSpace.AUDIT_TYPE,
          Prerequisite.AUDIT_TYPE,
          DocumentDefinition.AUDIT_TYPE);
    }
  }
}
