package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.LAST_PLATFORM_ADMIN_DISABLE;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class LastPlatformAdminDisableException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public LastPlatformAdminDisableException() {
    super(ErrorCategory.CONFLICT, LAST_PLATFORM_ADMIN_DISABLE);
  }
}
