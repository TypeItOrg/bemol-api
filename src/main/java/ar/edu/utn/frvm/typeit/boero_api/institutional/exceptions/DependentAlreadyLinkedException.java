package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class DependentAlreadyLinkedException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public DependentAlreadyLinkedException() {
    super(
        ErrorCategory.CONFLICT,
        InstitutionMessages.DEPENDENT_ALREADY_LINKED,
        "DEPENDENT_ALREADY_LINKED");
  }
}
