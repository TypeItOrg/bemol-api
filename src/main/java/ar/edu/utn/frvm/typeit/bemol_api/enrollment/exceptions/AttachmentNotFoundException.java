package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import java.io.Serial;
import java.util.UUID;

public class AttachmentNotFoundException extends ApplicationException {

  @Serial private static final long serialVersionUID = 1L;

  public AttachmentNotFoundException(UUID attachmentId) {
    super(ErrorCategory.NOT_FOUND, EnrollmentMessages.ATTACHMENT_ID_NOT_FOUND + attachmentId);
  }

  public AttachmentNotFoundException(String message) {
    super(ErrorCategory.NOT_FOUND, message);
  }
}
