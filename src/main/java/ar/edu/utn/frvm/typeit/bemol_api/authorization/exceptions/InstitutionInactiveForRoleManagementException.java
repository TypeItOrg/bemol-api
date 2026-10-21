package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.INSTITUTION_INACTIVE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionInactiveForRoleManagementException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InstitutionInactiveForRoleManagementException() {
    super(ErrorCategory.CONFLICT, INSTITUTION_INACTIVE);
  }
}
