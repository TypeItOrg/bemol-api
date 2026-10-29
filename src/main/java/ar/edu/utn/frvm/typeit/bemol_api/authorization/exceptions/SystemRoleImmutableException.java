package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.SYSTEM_ROLE_IMMUTABLE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class SystemRoleImmutableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public SystemRoleImmutableException() {
    super(ErrorCategory.AUTHORIZATION, SYSTEM_ROLE_IMMUTABLE);
  }
}
