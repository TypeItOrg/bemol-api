package ar.edu.utn.frvm.typeit.boero_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import java.util.Map;

public class GuardianMustBeAdultException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public GuardianMustBeAdultException() {
    super(
        ErrorCategory.INVALID_INPUT,
        AuthMessages.GUARDIAN_MUST_BE_ADULT,
        Map.of("birthDate", AuthMessages.GUARDIAN_MUST_BE_ADULT));
  }
}
