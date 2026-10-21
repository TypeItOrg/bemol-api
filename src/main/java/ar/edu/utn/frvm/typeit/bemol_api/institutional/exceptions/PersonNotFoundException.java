package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages.PERSON_NOT_FOUND;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PersonNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PersonNotFoundException() {
    super(ErrorCategory.NOT_FOUND, PERSON_NOT_FOUND);
  }
}
