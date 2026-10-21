package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.ROLE_NOT_FOUND;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RoleNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RoleNotFoundException() {
    super(ErrorCategory.NOT_FOUND, ROLE_NOT_FOUND);
  }
}
