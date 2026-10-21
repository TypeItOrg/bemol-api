package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.LOGIN_STATE_INCONSISTENT;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class LoginStateInconsistentException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public LoginStateInconsistentException() {
    super(ErrorCategory.AUTHENTICATION, LOGIN_STATE_INCONSISTENT);
  }
}
