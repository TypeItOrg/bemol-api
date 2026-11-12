package ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentRequirementLevel {
  AT_SUBMISSION("Obligatorio al enviar"),
  BEFORE_CONFIRMATION("Obligatorio para confirmar"),
  OPTIONAL("Opcional");

  private final String label;
}
