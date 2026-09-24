package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class DependentNotFoundException extends ApplicationException {

  public DependentNotFoundException() {
    super(ErrorCategory.NOT_FOUND, InstitutionMessages.DEPENDENT_NOT_FOUND, "DEPENDENT_NOT_FOUND");
  }
}
