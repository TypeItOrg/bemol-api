package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.INVALID_CREDENTIALS;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidCredentialsException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super(ErrorCategory.AUTHENTICATION, INVALID_CREDENTIALS);
  }
}
