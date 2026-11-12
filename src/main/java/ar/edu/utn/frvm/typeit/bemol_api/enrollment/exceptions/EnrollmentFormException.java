package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class EnrollmentFormException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public EnrollmentFormException(final ErrorCategory category, final String message) {
    super(category, message);
  }
}
