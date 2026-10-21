package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class InvalidStorageKeyException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidStorageKeyException() {
    super(ErrorCategory.INVALID_INPUT, StorageMessages.KEY_INVALID);
  }
}
