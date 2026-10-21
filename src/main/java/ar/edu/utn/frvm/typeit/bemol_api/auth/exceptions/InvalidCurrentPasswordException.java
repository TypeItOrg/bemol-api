package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.Map;

public class InvalidCurrentPasswordException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidCurrentPasswordException() {
    super(
        ErrorCategory.INVALID_INPUT,
        AuthMessages.INVALID_CURRENT_PASSWORD,
        Map.of("currentPassword", AuthMessages.INVALID_CURRENT_PASSWORD));
  }
}
