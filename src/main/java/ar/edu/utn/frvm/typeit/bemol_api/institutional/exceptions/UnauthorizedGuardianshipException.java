package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;

public class UnauthorizedGuardianshipException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public UnauthorizedGuardianshipException() {
    super(
        ErrorCategory.AUTHORIZATION,
        InstitutionMessages.GUARDIANSHIP_UNAUTHORIZED,
        "GUARDIANSHIP_UNAUTHORIZED");
  }
}
