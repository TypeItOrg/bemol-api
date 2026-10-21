package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InstitutionLogoNotFoundException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public InstitutionLogoNotFoundException() {
    super(ErrorCategory.NOT_FOUND, InstitutionMessages.LOGO_NOT_FOUND);
  }
}
