package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.LOGIN_ACCOUNT_NOT_FOUND;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class LoginAccountNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public LoginAccountNotFoundException() {
    super(ErrorCategory.NOT_FOUND, LOGIN_ACCOUNT_NOT_FOUND);
  }
}
