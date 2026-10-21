package ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.FieldConflictException;
import java.io.Serial;

public class PublicSubdomainAlreadyExistsException extends FieldConflictException {
  @Serial private static final long serialVersionUID = 1L;

  public PublicSubdomainAlreadyExistsException() {
    super("publicSubdomain", InstitutionMessages.PUBLIC_SUBDOMAIN_ALREADY_EXISTS);
  }
}
