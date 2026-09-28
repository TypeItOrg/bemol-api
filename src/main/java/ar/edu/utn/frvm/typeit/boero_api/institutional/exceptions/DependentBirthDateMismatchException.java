package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import java.util.Map;

public class DependentBirthDateMismatchException extends ApplicationException {

  public DependentBirthDateMismatchException() {
    super(
        ErrorCategory.INVALID_INPUT,
        InstitutionMessages.DEPENDENT_BIRTH_DATE_MISMATCH,
        Map.of("birthDate", InstitutionMessages.DEPENDENT_BIRTH_DATE_MISMATCH),
        "DEPENDENT_BIRTH_DATE_MISMATCH");
  }
}
