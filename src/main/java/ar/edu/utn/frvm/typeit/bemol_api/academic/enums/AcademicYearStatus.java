package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcademicYearStatus {
  PLANNED("Planificado"),
  ACTIVE("Activo"),
  CLOSED("Cerrado");

  private final String label;
}
