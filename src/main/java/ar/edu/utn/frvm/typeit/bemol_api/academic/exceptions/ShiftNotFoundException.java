package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class ShiftNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public ShiftNotFoundException() {
    super(AcademicMessages.SHIFT_NOT_FOUND);
  }
}
