package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PASSKEY_NOT_FOUND;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PasskeyNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PasskeyNotFoundException() {
    super(ErrorCategory.NOT_FOUND, PASSKEY_NOT_FOUND);
  }
}
