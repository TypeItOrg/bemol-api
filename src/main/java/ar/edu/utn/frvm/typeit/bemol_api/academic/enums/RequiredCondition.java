package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequiredCondition {
  REGULAR("Regularizada"),
  PASSED("Aprobada");

  private final String label;
}
