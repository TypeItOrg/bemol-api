package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionalContextMismatchException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public InstitutionalContextMismatchException() {
    super(ErrorCategory.AUTHORIZATION, AuthMessages.INSTITUTIONAL_CONTEXT_MISMATCH);
  }
}
