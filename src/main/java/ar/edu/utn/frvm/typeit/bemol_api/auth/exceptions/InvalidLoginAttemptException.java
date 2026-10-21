package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.LOGIN_ATTEMPT_INVALID;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidLoginAttemptException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidLoginAttemptException() {
    super(ErrorCategory.AUTHENTICATION, LOGIN_ATTEMPT_INVALID);
  }
}
