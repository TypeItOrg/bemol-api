package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import java.util.Map;

public class DependentMustBeMinorException extends ApplicationException {

  public DependentMustBeMinorException() {
    super(
        ErrorCategory.INVALID_INPUT,
        InstitutionMessages.DEPENDENT_MUST_BE_MINOR,
        Map.of("birthDate", InstitutionMessages.DEPENDENT_MUST_BE_MINOR));
  }
}
