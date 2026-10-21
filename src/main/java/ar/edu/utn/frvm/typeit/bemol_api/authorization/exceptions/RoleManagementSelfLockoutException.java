package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.ROLE_MANAGEMENT_SELF_LOCKOUT;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RoleManagementSelfLockoutException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RoleManagementSelfLockoutException() {
    super(ErrorCategory.CONFLICT, ROLE_MANAGEMENT_SELF_LOCKOUT);
  }
}
