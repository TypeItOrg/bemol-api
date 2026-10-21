package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class StorageFileNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public StorageFileNotFoundException() {
    super(ErrorCategory.NOT_FOUND, StorageMessages.FILE_NOT_FOUND);
  }
}
