package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcademicSpaceType {
  SUBJECT("Asignatura"),
  WORKSHOP("Taller"),
  SEMINAR("Seminario"),
  PRACTICE("Práctica"),
  OTHER("Otro");

  private final String label;
}
