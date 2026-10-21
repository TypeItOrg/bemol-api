package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.ROLE_WITH_ASSIGNMENTS;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RoleWithAssignmentsException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RoleWithAssignmentsException() {
    super(ErrorCategory.CONFLICT, ROLE_WITH_ASSIGNMENTS);
  }
}
