package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class PrerequisiteNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public PrerequisiteNotFoundException() {
    super(AcademicMessages.PREREQUISITE_NOT_FOUND);
  }
}
