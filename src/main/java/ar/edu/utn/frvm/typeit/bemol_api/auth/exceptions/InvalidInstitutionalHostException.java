package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidInstitutionalHostException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public InvalidInstitutionalHostException() {
    super(ErrorCategory.INVALID_INPUT, AuthMessages.INSTITUTIONAL_HOST_INVALID);
  }
}
