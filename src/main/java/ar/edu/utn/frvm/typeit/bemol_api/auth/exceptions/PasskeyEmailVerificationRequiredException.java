package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PasskeyEmailVerificationRequiredException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public PasskeyEmailVerificationRequiredException() {
    super(ErrorCategory.AUTHENTICATION, AuthMessages.PASSKEY_EMAIL_VERIFICATION_REQUIRED);
  }
}
