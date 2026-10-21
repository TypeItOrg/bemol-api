package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.RECENT_AUTH_REQUIRED;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class RecentAuthRequiredException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public static final String CODE = "RECENT_AUTHENTICATION_REQUIRED";

  public RecentAuthRequiredException() {
    super(ErrorCategory.AUTHORIZATION, RECENT_AUTH_REQUIRED, CODE);
  }
}
