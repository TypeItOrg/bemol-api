package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class CannotGuardianSelfException extends ApplicationException {

  public CannotGuardianSelfException() {
    super(
        ErrorCategory.INVALID_INPUT,
        InstitutionMessages.CANNOT_GUARDIAN_SELF,
        "CANNOT_GUARDIAN_SELF");
  }
}
