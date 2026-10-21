package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.LAST_PERSON_ROLE_REVOCATION;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class LastPersonRoleRevocationException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public LastPersonRoleRevocationException() {
    super(ErrorCategory.CONFLICT, LAST_PERSON_ROLE_REVOCATION);
  }
}
