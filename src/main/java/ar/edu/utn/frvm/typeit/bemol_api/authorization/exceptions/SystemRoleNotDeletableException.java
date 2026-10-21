package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.SYSTEM_ROLE_NOT_DELETABLE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class SystemRoleNotDeletableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public SystemRoleNotDeletableException() {
    super(ErrorCategory.INVALID_INPUT, SYSTEM_ROLE_NOT_DELETABLE);
  }
}
