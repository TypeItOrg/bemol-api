package ar.edu.utn.frvm.typeit.bemol_api.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermissionGroup {
  PEOPLE("Personas", "Acceso a datos y operaciones sobre usuarios institucionales."),
  ROLES("Roles y permisos", "Administración y asignación de responsabilidades."),
  GRADES("Calificaciones", "Carga y gestión de calificaciones académicas."),
  ACADEMIC("Académico", "Configuración de la estructura curricular institucional."),
  ENROLLMENT("Inscripciones", "Gestión de solicitudes de inscripción."),
  ACTIVITY("Actividad", "Consulta de auditoría y envíos de correos de la institución."),
  INSTITUTION("Institución", "Gestión y visualización de datos de la institución.");

  private final String displayName;
  private final String description;
}
