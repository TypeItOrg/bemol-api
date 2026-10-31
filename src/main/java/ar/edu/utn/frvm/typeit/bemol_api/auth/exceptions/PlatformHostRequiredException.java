package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class PlatformHostRequiredException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public PlatformHostRequiredException() {
    super(ErrorCategory.AUTHORIZATION, AuthMessages.PLATFORM_HOST_REQUIRED);
  }
}
