package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidEnrollmentApplicationStateException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidEnrollmentApplicationStateException(final String message) {
    super(ErrorCategory.CONFLICT, message);
  }
}
