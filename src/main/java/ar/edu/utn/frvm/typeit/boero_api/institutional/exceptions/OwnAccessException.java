package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class OwnAccessException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public OwnAccessException(final String message) {
    super(ErrorCategory.INVALID_INPUT, message, "OWN_ACCESS_INVALID");
  }
}
