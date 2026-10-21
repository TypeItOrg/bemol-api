package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.ROLE_REVOCATION_NOT_ALLOWED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RoleRevocationNotAllowedException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RoleRevocationNotAllowedException() {
    super(ErrorCategory.AUTHORIZATION, ROLE_REVOCATION_NOT_ALLOWED);
  }
}
