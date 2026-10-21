package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.WEBAUTHN_VERIFICATION_FAILED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class WebAuthnVerificationFailedException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public WebAuthnVerificationFailedException() {
    super(ErrorCategory.AUTHENTICATION, WEBAUTHN_VERIFICATION_FAILED);
  }
}
