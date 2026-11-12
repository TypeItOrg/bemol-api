package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalMode {
  PROMOTION("Promoción"),
  FINAL_EXAM("Examen final"),
  PROMOTION_OR_FINAL_EXAM("Promoción o examen final");

  private final String label;
}
