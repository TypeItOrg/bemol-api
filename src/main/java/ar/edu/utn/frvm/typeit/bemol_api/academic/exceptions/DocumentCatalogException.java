package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.Map;

public class DocumentCatalogException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public DocumentCatalogException(final ErrorCategory category, final String message) {
    super(category, message);
  }

  public DocumentCatalogException(
      final ErrorCategory category, final String message, final Map<String, String> fieldErrors) {
    super(category, message, fieldErrors);
  }
}
