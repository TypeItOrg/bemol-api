package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PersonAlreadyExistsException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PersonAlreadyExistsException() {
    super(ErrorCategory.CONFLICT, InstitutionMessages.PERSON_ALREADY_EXISTS);
  }
}
