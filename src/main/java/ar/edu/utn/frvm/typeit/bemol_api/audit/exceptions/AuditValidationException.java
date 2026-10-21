package ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class AuditValidationException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public AuditValidationException(final String message) {
    super(ErrorCategory.INVALID_INPUT, message);
  }
}
