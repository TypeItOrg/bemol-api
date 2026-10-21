package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.Map;

public class AcademicConflictException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public static AcademicConflictException forField(final String field, final String message) {
    return new AcademicConflictException(message, Map.of(field, message));
  }

  public AcademicConflictException(final String message) {
    super(ErrorCategory.CONFLICT, message);
  }

  public AcademicConflictException(final String message, final Map<String, String> fieldErrors) {
    super(ErrorCategory.CONFLICT, message, fieldErrors);
  }
}
