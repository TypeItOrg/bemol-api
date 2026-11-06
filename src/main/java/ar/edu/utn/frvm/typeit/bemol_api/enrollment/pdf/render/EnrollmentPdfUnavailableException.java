package ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import java.io.Serial;

public class EnrollmentPdfUnavailableException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public EnrollmentPdfUnavailableException() {
    super(ErrorCategory.UNAVAILABLE, EnrollmentMessages.PDF_UNAVAILABLE);
  }

  public int retryAfterSeconds() {
    return 5;
  }
}
