package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class AcademicSpaceNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public AcademicSpaceNotFoundException() {
    super(AcademicMessages.ACADEMIC_SPACE_NOT_FOUND);
  }
}
