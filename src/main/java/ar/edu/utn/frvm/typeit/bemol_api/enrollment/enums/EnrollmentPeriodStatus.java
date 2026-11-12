package ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnrollmentPeriodStatus {
  PLANNED("Planificado"),
  OPEN("Abierto"),
  CLOSED("Cerrado");

  private final String label;
}
