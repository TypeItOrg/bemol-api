package ar.edu.utn.frvm.typeit.boero_api.audit.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import java.io.Serial;

public class AuditEventNotFoundException extends ApplicationException {
  @Serial private static final long serialVersionUID = 1L;

  public AuditEventNotFoundException() {
    super(ErrorCategory.NOT_FOUND, AuditMessages.EVENT_NOT_FOUND);
  }
}
