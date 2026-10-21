package ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums;

public enum EducationLevel {
  NO_SCHOOLING,
  INITIAL,
  PRIMARY,
  SECONDARY,
  NON_UNIVERSITY_HIGHER,
  UNIVERSITY;

  public boolean requiresSecondaryCompletionAnswer() {
    return this == SECONDARY || this == NON_UNIVERSITY_HIGHER || this == UNIVERSITY;
  }
}
