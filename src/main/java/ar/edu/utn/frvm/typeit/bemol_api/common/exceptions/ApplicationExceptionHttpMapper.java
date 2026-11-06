package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render.EnrollmentPdfUnavailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ApplicationExceptionHttpMapper {

  public ExceptionPayload validationError(final Map<String, String> fieldErrors) {
    return ExceptionPayload.builder()
        .status(HttpStatus.BAD_REQUEST.value())
        .message(ErrorMessages.VALIDATION_ERROR_MESSAGE)
        .fieldErrors(fieldErrors)
        .build();
  }

  public ResponseEntity<ExceptionPayload> toResponse(final ApplicationException exception) {
    final HttpStatus status = statusFor(exception.category());
    final var response = ResponseEntity.status(status);
    if (exception instanceof EnrollmentPdfUnavailableException unavailable) {
      response.header("Retry-After", Integer.toString(unavailable.retryAfterSeconds()));
    }
    return response.body(
        ExceptionPayload.builder()
            .status(status.value())
            .message(exception.getMessage())
            .fieldErrors(exception.fieldErrors())
            .code(exception.code())
            .build());
  }

  private static HttpStatus statusFor(final ErrorCategory category) {
    return switch (category) {
      case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
      case AUTHENTICATION -> HttpStatus.UNAUTHORIZED;
      case AUTHORIZATION -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case CONFLICT -> HttpStatus.CONFLICT;
    };
  }
}
