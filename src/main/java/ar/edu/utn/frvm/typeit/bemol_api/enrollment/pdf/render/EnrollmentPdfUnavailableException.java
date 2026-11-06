package ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;

public class EnrollmentPdfUnavailableException extends ApplicationException {
  public EnrollmentPdfUnavailableException() {
    super(ErrorCategory.UNAVAILABLE, EnrollmentMessages.PDF_UNAVAILABLE);
  }

  public int retryAfterSeconds() {
    return 5;
  }
}
