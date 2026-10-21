package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PASSKEY_LIMIT_EXCEEDED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PasskeyLimitExceededException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PasskeyLimitExceededException() {
    super(ErrorCategory.CONFLICT, PASSKEY_LIMIT_EXCEEDED);
  }
}
