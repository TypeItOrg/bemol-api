package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class GuardianLinkNotFoundException extends ApplicationException {

  public GuardianLinkNotFoundException() {
    super(
        ErrorCategory.NOT_FOUND,
        InstitutionMessages.GUARDIAN_LINK_NOT_FOUND,
        "GUARDIAN_LINK_NOT_FOUND");
  }
}
