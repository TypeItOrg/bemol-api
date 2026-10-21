package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.UUID;

public class EnrollmentApplicationNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public EnrollmentApplicationNotFoundException() {
    super(ErrorCategory.NOT_FOUND, EnrollmentMessages.ENROLLMENT_APPLICATION_NOT_FOUND);
  }

  public EnrollmentApplicationNotFoundException(final UUID applicationId) {
    super(ErrorCategory.NOT_FOUND, EnrollmentMessages.APPLICATION_ID_NOT_FOUND + applicationId);
  }
}
