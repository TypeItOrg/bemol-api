package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class InvalidAcademicStateException extends AcademicConflictException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidAcademicStateException() {
    super(AcademicMessages.INVALID_STATE);
  }

  public InvalidAcademicStateException(final String message) {
    super(message);
  }
}
