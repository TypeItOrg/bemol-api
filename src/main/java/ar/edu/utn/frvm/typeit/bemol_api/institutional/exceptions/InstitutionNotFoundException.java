package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages.INSTITUTION_NOT_FOUND;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InstitutionNotFoundException() {
    super(ErrorCategory.NOT_FOUND, INSTITUTION_NOT_FOUND);
  }
}
