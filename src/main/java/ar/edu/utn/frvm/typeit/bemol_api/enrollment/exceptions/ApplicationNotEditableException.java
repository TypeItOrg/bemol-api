package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.UUID;

public class ApplicationNotEditableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public ApplicationNotEditableException(UUID applicationId) {
    super(ErrorCategory.CONFLICT, EnrollmentMessages.APPLICATION_NOT_EDITABLE);
  }
}
