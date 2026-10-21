package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PASSKEY_ALREADY_REGISTERED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class DuplicatePasskeyCredentialException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public DuplicatePasskeyCredentialException() {
    super(ErrorCategory.CONFLICT, PASSKEY_ALREADY_REGISTERED);
  }
}
