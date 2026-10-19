package ar.edu.utn.frvm.typeit.boero_api.audit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditModule {
  USERS("Usuarios"),
  ROLES("Roles"),
  GUARDIAN_LINKS("Vinculaciones"),
  ENROLLMENTS("Inscripciones"),
  INSTITUTIONS("Instituciones"),
  COURSES("Cursos"),
  STUDY_PLANS("Planes de estudio"),
  DOCUMENTATION("Documentación"),
  EMAILS("Correos"),
  TRAINING_PATHS("Trayectos formativos"),
  ACADEMIC_YEARS("Ciclos lectivos"),
  ACADEMIC_SPACES("Espacios académicos"),
  INSTRUMENTS("Instrumentos"),
  SHIFTS("Turnos"),
  ENROLLMENT_PERIODS("Períodos de inscripción"),
  COURSE_ENROLLMENTS("Cursadas"),
  SECURITY("Seguridad");

  private final String label;
}
