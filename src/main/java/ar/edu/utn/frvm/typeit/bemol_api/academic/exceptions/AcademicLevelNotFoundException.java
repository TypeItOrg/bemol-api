package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class AcademicLevelNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public AcademicLevelNotFoundException() {
    super(AcademicMessages.ACADEMIC_LEVEL_NOT_FOUND);
  }
}
