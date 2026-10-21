package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.ROLE_ASSIGNMENT_NOT_ALLOWED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RoleAssignmentNotAllowedException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public RoleAssignmentNotAllowedException() {
    super(ErrorCategory.AUTHORIZATION, ROLE_ASSIGNMENT_NOT_ALLOWED);
  }
}
