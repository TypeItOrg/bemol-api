package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionLogoTooLargeException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public InstitutionLogoTooLargeException() {
    super(ErrorCategory.INVALID_INPUT, InstitutionMessages.LOGO_TOO_LARGE);
  }
}
