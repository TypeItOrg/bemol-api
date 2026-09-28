package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class DependentHasAccountException extends ApplicationException {

  public DependentHasAccountException() {
    super(
        ErrorCategory.CONFLICT, InstitutionMessages.DEPENDENT_HAS_ACCOUNT, "DEPENDENT_HAS_ACCOUNT");
  }
}
