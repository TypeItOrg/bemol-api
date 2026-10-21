package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages.SLUG_ALREADY_EXISTS;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.FieldConflictException;
import java.io.Serial;

public class SlugAlreadyExistsException extends FieldConflictException {

  @Serial private static final long serialVersionUID = 1L;

  public SlugAlreadyExistsException() {
    super("slug", SLUG_ALREADY_EXISTS);
  }
}
