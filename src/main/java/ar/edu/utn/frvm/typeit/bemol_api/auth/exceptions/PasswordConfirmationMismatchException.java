package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PasswordConfirmationMismatchException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PasswordConfirmationMismatchException() {
    super(ErrorCategory.INVALID_INPUT, AuthMessages.PASSWORD_CONFIRMATION_MISMATCH);
  }
}
