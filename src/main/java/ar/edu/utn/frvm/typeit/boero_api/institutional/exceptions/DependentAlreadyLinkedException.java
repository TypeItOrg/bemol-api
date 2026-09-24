package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class DependentAlreadyLinkedException extends ApplicationException {

  public DependentAlreadyLinkedException() {
    super(
        ErrorCategory.CONFLICT,
        InstitutionMessages.DEPENDENT_ALREADY_LINKED,
        "DEPENDENT_ALREADY_LINKED");
  }
}
