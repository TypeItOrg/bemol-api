package ar.edu.utn.frvm.typeit.boero_api.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermissionGroup {
  PEOPLE("Personas", "Acceso a datos y operaciones sobre usuarios institucionales."),
  ROLES("Roles y permisos", "Administración y asignación de responsabilidades."),
  ACADEMIC("Académico", "Configuración de la estructura curricular institucional."),
  ENROLLMENT("Inscripciones", "Gestión de solicitudes de inscripción."),
  INSTITUTION("Institución", "Gestión y visualización de datos de la institución.");

  private final String displayName;
  private final String description;
}
