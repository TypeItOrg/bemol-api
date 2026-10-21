package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.PERSON_NOT_FOUND_IN_INSTITUTION;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PersonNotFoundInInstitutionException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PersonNotFoundInInstitutionException() {
    super(ErrorCategory.NOT_FOUND, PERSON_NOT_FOUND_IN_INSTITUTION);
  }
}
