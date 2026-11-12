package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import jakarta.validation.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

public class ApplicationExceptionHttpMapper {

  @Nullable ApplicationException revisionConflict(
      final ObjectOptimisticLockingFailureException exception) {
    final var type = Objects.toString(exception.getPersistentClassName(), "");
    if (Set.of(
            EnrollmentForm.class.getName(),
            EnrollmentFormVersion.class.getName(),
            EnrollmentFormAssignment.class.getName())
        .contains(type)) {
      return new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.REVISION_CONFLICT);
    }
    if (Set.of(DocumentDefinition.class.getName(), TrainingPathDocumentRequirement.class.getName())
        .contains(type)) {
      return new DocumentCatalogException(
          ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_REVISION_CONFLICT);
    }
    return null;
  }

  public ExceptionPayload validationError(final Map<String, String> fieldErrors) {
    return ExceptionPayload.builder()
        .status(HttpStatus.BAD_REQUEST.value())
        .message(ErrorMessages.VALIDATION_ERROR_MESSAGE)
        .fieldErrors(fieldErrors)
        .build();
  }

  public ResponseEntity<ExceptionPayload> toResponse(final ApplicationException exception) {
    final HttpStatus status = statusFor(exception.category());
    return ResponseEntity.status(status)
        .body(
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

  static String lastPropertyName(Path propertyPath) {
    String name = propertyPath.toString();
    for (Path.Node node : propertyPath) {
      if (node instanceof Path.PropertyNode propertyNode) {
        name = propertyNode.getName();
      }
    }
    return name;
  }
}
