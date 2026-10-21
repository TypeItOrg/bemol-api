package ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.USER_ALREADY_EXISTS;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.FieldConflictException;
import java.io.Serial;

public class UserAlreadyExistsException extends FieldConflictException {

  @Serial private static final long serialVersionUID = 1L;

  public UserAlreadyExistsException() {
    super("documentNumber", USER_ALREADY_EXISTS);
  }
}
