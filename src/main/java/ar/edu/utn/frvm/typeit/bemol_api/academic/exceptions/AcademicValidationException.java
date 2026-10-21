package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.Map;

public class AcademicValidationException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public AcademicValidationException(final String message) {
    super(ErrorCategory.INVALID_INPUT, message);
  }

  public AcademicValidationException(final String message, final Map<String, String> fieldErrors) {
    super(ErrorCategory.INVALID_INPUT, message, fieldErrors);
  }
}
