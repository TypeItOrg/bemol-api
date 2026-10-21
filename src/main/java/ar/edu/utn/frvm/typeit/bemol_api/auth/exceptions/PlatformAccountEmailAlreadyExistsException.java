package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PLATFORM_ACCOUNT_EMAIL_ALREADY_EXISTS;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.FieldConflictException;
import java.io.Serial;

public class PlatformAccountEmailAlreadyExistsException extends FieldConflictException {

  @Serial private static final long serialVersionUID = 1L;

  public PlatformAccountEmailAlreadyExistsException() {
    super("email", PLATFORM_ACCOUNT_EMAIL_ALREADY_EXISTS);
  }
}
