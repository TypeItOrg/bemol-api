package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcademicSpaceFormat {
  INDIVIDUAL("Individual"),
  GRUPAL("Grupal");

  private final String label;
}
