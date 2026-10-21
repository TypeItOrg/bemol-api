package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_TOKEN_REUSE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RefreshTokenReuseException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RefreshTokenReuseException() {
    super(ErrorCategory.AUTHENTICATION, REFRESH_TOKEN_REUSE);
  }
}
