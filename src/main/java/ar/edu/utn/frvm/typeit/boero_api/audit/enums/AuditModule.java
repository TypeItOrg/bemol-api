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
  DOCUMENTATION("Documentación");

  private final String label;
}
