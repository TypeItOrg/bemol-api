package ar.edu.utn.frvm.typeit.boero_api.common.exceptions;

public class InvalidRequestParameterException extends ApplicationException {
  private static final long serialVersionUID = 1L;

  public InvalidRequestParameterException() {
    super(
        ErrorCategory.INVALID_INPUT,
        ErrorMessages.INVALID_REQUEST_PARAMETER,
        "INVALID_REQUEST_PARAMETER");
  }
}
