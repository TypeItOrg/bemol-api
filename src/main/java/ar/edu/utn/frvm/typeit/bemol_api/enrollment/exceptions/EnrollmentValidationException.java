package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.Map;

public class EnrollmentValidationException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public EnrollmentValidationException(final String message) {
    super(ErrorCategory.INVALID_INPUT, message);
  }

  public EnrollmentValidationException(
      final String message, final Map<String, String> fieldErrors) {
    super(ErrorCategory.INVALID_INPUT, message, fieldErrors);
  }
}
