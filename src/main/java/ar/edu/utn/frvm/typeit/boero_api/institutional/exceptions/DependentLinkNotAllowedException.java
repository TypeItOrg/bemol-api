package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class DependentLinkNotAllowedException extends ApplicationException {

  public DependentLinkNotAllowedException() {
    super(
        ErrorCategory.CONFLICT,
        InstitutionMessages.DEPENDENT_LINK_NOT_ALLOWED,
        "DEPENDENT_LINK_NOT_ALLOWED");
  }
}
