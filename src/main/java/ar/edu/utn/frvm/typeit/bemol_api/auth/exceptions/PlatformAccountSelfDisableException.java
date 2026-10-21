package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PLATFORM_ACCOUNT_SELF_DISABLE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PlatformAccountSelfDisableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public PlatformAccountSelfDisableException() {
    super(ErrorCategory.CONFLICT, PLATFORM_ACCOUNT_SELF_DISABLE);
  }
}
