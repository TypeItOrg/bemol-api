package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyPlanStatus {
  DRAFT("Borrador"),
  ACTIVE("Activo"),
  INACTIVE("Inactivo");

  private final String label;
}
