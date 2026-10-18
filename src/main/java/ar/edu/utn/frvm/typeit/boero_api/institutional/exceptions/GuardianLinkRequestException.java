package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class GuardianLinkRequestException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public GuardianLinkRequestException() {
    super(
        ErrorCategory.INVALID_INPUT,
        "No se pudo enviar la solicitud. Revisá los datos o consultá a la institución.",
        "GUARDIAN_LINK_REQUEST_INVALID");
  }
}
