package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class UnauthorizedGuardianshipException extends ApplicationException {

  public UnauthorizedGuardianshipException() {
    super(
        ErrorCategory.AUTHORIZATION,
        InstitutionMessages.GUARDIANSHIP_UNAUTHORIZED,
        "GUARDIANSHIP_UNAUTHORIZED");
  }
}
