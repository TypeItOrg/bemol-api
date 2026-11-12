package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CourseStatus {
  ACTIVE("Activo"),
  INACTIVE("Inactivo"),
  CLOSED("Cerrado");

  private final String label;
}
