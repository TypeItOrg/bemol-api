package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidAccessScopeException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidAccessScopeException() {
    super(ErrorCategory.INVALID_INPUT, AuthorizationMessages.INVALID_ACCESS_SCOPE);
  }
}
