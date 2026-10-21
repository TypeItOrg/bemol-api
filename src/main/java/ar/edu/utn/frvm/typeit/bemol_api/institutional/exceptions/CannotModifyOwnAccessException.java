package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages.CANNOT_MODIFY_OWN_ACCESS;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class CannotModifyOwnAccessException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public CannotModifyOwnAccessException() {
    super(ErrorCategory.INVALID_INPUT, CANNOT_MODIFY_OWN_ACCESS);
  }
}
