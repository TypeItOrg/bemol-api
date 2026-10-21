package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.DUPLICATE_ROLE_NAME;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class DuplicateRoleNameException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public DuplicateRoleNameException() {
    super(ErrorCategory.CONFLICT, DUPLICATE_ROLE_NAME);
  }
}
