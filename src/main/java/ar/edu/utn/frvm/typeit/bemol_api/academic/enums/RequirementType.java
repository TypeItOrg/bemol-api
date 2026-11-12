package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequirementType {
  REQUIRED("Obligatorio"),
  OPTIONAL("Optativo");

  private final String label;
}
