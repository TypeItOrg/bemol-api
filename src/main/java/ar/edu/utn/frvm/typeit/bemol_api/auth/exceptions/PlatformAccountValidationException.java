package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.Map;

public class PlatformAccountValidationException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public PlatformAccountValidationException(final String message, final String field) {
    super(ErrorCategory.INVALID_INPUT, message, Map.of(field, message));
  }
}
