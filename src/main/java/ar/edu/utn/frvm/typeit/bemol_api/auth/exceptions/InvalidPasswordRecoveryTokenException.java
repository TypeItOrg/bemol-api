package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidPasswordRecoveryTokenException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidPasswordRecoveryTokenException() {
    super(ErrorCategory.INVALID_INPUT, AuthMessages.PASSWORD_RECOVERY_TOKEN_INVALID);
  }
}
