package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.ROLE_NOT_ASSIGNABLE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RoleNotAssignableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RoleNotAssignableException() {
    super(ErrorCategory.INVALID_INPUT, ROLE_NOT_ASSIGNABLE);
  }
}
