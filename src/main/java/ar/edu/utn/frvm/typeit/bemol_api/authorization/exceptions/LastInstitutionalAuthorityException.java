package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.LAST_INSTITUTIONAL_AUTHORITY;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class LastInstitutionalAuthorityException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public LastInstitutionalAuthorityException() {
    super(ErrorCategory.CONFLICT, LAST_INSTITUTIONAL_AUTHORITY);
  }
}
