package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import java.io.Serial;
import java.util.Map;

public class FieldConflictException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public FieldConflictException(final String field, final String message) {
    super(ErrorCategory.CONFLICT, message, Map.of(field, message));
  }
}
