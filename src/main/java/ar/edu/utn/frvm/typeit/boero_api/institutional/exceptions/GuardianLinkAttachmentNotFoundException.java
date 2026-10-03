package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;

public class GuardianLinkAttachmentNotFoundException extends ApplicationException {

  public GuardianLinkAttachmentNotFoundException() {
    super(
        ErrorCategory.NOT_FOUND,
        InstitutionMessages.GUARDIAN_LINK_ATTACHMENT_NOT_FOUND,
        "GUARDIAN_LINK_ATTACHMENT_NOT_FOUND");
  }
}
