package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidPublicSubdomainException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public InvalidPublicSubdomainException() {
    super(ErrorCategory.INVALID_INPUT, InstitutionMessages.PUBLIC_SUBDOMAIN_INVALID);
  }

  public InvalidPublicSubdomainException(final String message) {
    super(ErrorCategory.INVALID_INPUT, message);
  }
}
