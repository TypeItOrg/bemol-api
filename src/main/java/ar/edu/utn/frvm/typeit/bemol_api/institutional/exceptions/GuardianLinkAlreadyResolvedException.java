package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;

public class GuardianLinkAlreadyResolvedException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public GuardianLinkAlreadyResolvedException() {
    super(
        ErrorCategory.CONFLICT,
        InstitutionMessages.GUARDIAN_LINK_ALREADY_RESOLVED,
        "GUARDIAN_LINK_ALREADY_RESOLVED");
  }
}
