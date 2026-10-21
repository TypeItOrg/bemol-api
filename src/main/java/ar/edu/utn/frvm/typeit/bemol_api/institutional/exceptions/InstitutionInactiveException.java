package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages.INSTITUTION_INACTIVE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionInactiveException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InstitutionInactiveException() {
    super(ErrorCategory.NOT_FOUND, INSTITUTION_INACTIVE);
  }
}
