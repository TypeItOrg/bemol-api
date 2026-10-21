package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class ScopedResourceNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public ScopedResourceNotFoundException() {
    super(ErrorCategory.NOT_FOUND, AuthorizationMessages.SCOPED_RESOURCE_NOT_FOUND);
  }
}
