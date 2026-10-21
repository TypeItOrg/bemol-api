package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_TOKEN_INVALID;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidRefreshTokenException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidRefreshTokenException() {
    super(ErrorCategory.AUTHENTICATION, REFRESH_TOKEN_INVALID);
  }
}
