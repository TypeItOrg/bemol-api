package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.INSTITUTIONAL_AUTHORITY_ROLE_IMMUTABLE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionalAuthorityRoleImmutableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InstitutionalAuthorityRoleImmutableException() {
    super(ErrorCategory.INVALID_INPUT, INSTITUTIONAL_AUTHORITY_ROLE_IMMUTABLE);
  }
}
