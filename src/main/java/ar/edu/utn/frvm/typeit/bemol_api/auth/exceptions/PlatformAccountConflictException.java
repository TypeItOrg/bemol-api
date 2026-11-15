package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PLATFORM_ACCOUNT_DISABLED_SETUP_CODE;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PLATFORM_ACCOUNT_SELF_SETUP_CODE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PlatformAccountConflictException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  private PlatformAccountConflictException(final String message) {
    super(ErrorCategory.CONFLICT, message);
  }

  public static PlatformAccountConflictException selfSetupCode() {
    return new PlatformAccountConflictException(PLATFORM_ACCOUNT_SELF_SETUP_CODE);
  }

  public static PlatformAccountConflictException disabledSetupCode() {
    return new PlatformAccountConflictException(PLATFORM_ACCOUNT_DISABLED_SETUP_CODE);
  }
}
