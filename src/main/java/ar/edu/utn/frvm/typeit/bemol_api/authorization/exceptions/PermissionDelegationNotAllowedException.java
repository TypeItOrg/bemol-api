package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.PERMISSION_DELEGATION_NOT_ALLOWED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PermissionDelegationNotAllowedException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PermissionDelegationNotAllowedException() {
    super(ErrorCategory.AUTHORIZATION, PERMISSION_DELEGATION_NOT_ALLOWED);
  }
}
