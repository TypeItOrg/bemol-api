package ar.edu.utn.frvm.typeit.boero_api.common.exceptions;

import static ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorMessages.INTERNAL_SERVER_ERROR_MESSAGE;
import static ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorMessages.MALFORMED_REQUEST_BODY;
import static ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorMessages.RESOURCE_NOT_FOUND_MESSAGE;
import static ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorMessages.UNHANDLED_EXCEPTION_MESSAGE;
import static ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorMessages.VALIDATION_ERROR_MESSAGE;
import static ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private final ApplicationExceptionHttpMapper applicationExceptionHttpMapper =
      new ApplicationExceptionHttpMapper();

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ExceptionPayload handleException(Exception ex) {
    log.error(UNHANDLED_EXCEPTION_MESSAGE, ex.getMessage(), ex);
    return ExceptionPayload.builder()
        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .message(INTERNAL_SERVER_ERROR_MESSAGE)
        .build();
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ExceptionPayload> handleDocumentRevisionConflict(
      final ObjectOptimisticLockingFailureException exception) {
    if (!Set.of(DocumentDefinition.class.getName(), TrainingPathDocumentRequirement.class.getName())
        .contains(Objects.toString(exception.getPersistentClassName(), ""))) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(handleException(exception));
    }
    return handleApplicationException(
        new DocumentCatalogException(
            ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_REVISION_CONFLICT));
  }

  @ExceptionHandler({DataIntegrityViolationException.class, TransactionSystemException.class})
  public ResponseEntity<ExceptionPayload> handlePersistenceException(
      final RuntimeException exception) {
    final var scopeConstraints =
        Set.of(
            "person_role_assignments_access_scope_check",
            "person_role_assignment_scope_consistency",
            "role_scope_assignment_tenant_fk",
            "person_role_assignments_role_institution_fk",
            "role_scope_training_path_tenant_fk",
            "person_role_assignment_training_paths_pkey");
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
        final String constraint = Objects.toString(violation.getConstraintName(), "");
        if (Set.of(
                "document_definition_institution_change_check",
                "document_definition_usage_immutable_check")
            .contains(constraint)) {
          return handleApplicationException(
              new DocumentCatalogException(
                  ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_INSTITUTION_CHANGE_BLOCKED));
        }
        if (constraint.equals("training_path_document_identity_check")) {
          return handleApplicationException(
              new DocumentCatalogException(
                  ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_ASSIGNMENT_IMMUTABLE));
        }
        if (Set.of("training_path_document_unique", "enrollment_document_definition_unique")
            .contains(constraint)) {
          return handleApplicationException(
              new DocumentCatalogException(
                  ErrorCategory.CONFLICT,
                  constraint.equals("training_path_document_unique")
                      ? AcademicMessages.DOCUMENT_DUPLICATE_ASSIGNMENT
                      : EnrollmentMessages.DOCUMENT_REQUEST_DUPLICATE));
        }
        final String message =
            switch (constraint) {
              case "enrollment_document_request_closed_check" ->
                  EnrollmentMessages.DOCUMENT_REQUEST_CLOSED;
              case "enrollment_document_request_inactive_check" ->
                  EnrollmentMessages.DOCUMENT_REQUEST_INACTIVE;
              case "enrollment_document_request_immutable_check",
                  "enrollment_requirement_frozen_check",
                  "enrollment_requirement_identity_check" ->
                  EnrollmentMessages.DOCUMENT_REQUEST_IMMUTABLE;
              case "enrollment_requirement_retired_check" ->
                  EnrollmentMessages.DOCUMENT_REQUIREMENT_RETIRED;
              case "enrollment_document_format_check" -> EnrollmentMessages.DOCUMENT_FORMAT_CHANGED;
              case "training_path_document_tenant_fk",
                  "training_path_document_path_tenant_fk",
                  "enrollment_document_tenant_fk",
                  "enrollment_document_application_tenant_fk",
                  "enrollment_requirement_request_tenant_fk" ->
                  EnrollmentMessages.DOCUMENT_REQUIREMENT_NOT_FOUND;
              case "enrollment_document_gate_check" ->
                  EnrollmentMessages.DOCUMENT_CONFIRMATION_REQUIRED;
              case "enrollment_attachments_current_requirement_unique" ->
                  EnrollmentMessages.ATTACHMENT_TYPE_CONFLICT;
              case "enrollment_attachment_requirement_fk" ->
                  EnrollmentMessages.DOCUMENT_REQUIREMENT_NOT_FOUND;
              case "enrollment_attachment_review_check",
                  "enrollment_attachment_observation_check",
                  "enrollment_attachment_immutable_check" ->
                  EnrollmentMessages.DOCUMENT_REVIEW_INVALID;
              default -> null;
            };
        if (message != null) {
          return handleApplicationException(new EnrollmentValidationException(message));
        }
        if (scopeConstraints.contains(constraint)) {
          return handleApplicationException(new InvalidAccessScopeException());
        }
      }
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(handleException(exception));
  }

  @ExceptionHandler(ApplicationException.class)
  public ResponseEntity<ExceptionPayload> handleApplicationException(
      final ApplicationException exception) {
    return applicationExceptionHttpMapper.toResponse(exception);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ExceptionPayload handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    Map<String, String> fieldErrors =
        e.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    fe -> fe.getField(),
                    fe -> Objects.toString(fe.getDefaultMessage(), ""),
                    (existing, replacement) -> existing));
    return validationErrorPayload(fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ExceptionPayload handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
    return ExceptionPayload.builder()
        .status(HttpStatus.BAD_REQUEST.value())
        .message(MALFORMED_REQUEST_BODY)
        .build();
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ExceptionPayload handleInvalidRequestParameter(final Exception exception) {
    return ExceptionPayload.builder()
        .status(HttpStatus.BAD_REQUEST.value())
        .message(ErrorMessages.INVALID_REQUEST_PARAMETER)
        .build();
  }

  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ExceptionPayload handleNoResourceFound(NoResourceFoundException ex) {
    return ExceptionPayload.builder()
        .status(HttpStatus.NOT_FOUND.value())
        .message(RESOURCE_NOT_FOUND_MESSAGE)
        .build();
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ExceptionPayload handleConstraintViolationException(ConstraintViolationException e) {
    Map<String, String> fieldErrors =
        e.getConstraintViolations().stream()
            .collect(
                Collectors.toMap(
                    cv -> lastPropertyName(cv.getPropertyPath()),
                    cv -> cv.getMessage(),
                    (existing, replacement) -> existing));
    return validationErrorPayload(fieldErrors);
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ExceptionPayload handleAccessDeniedException(AccessDeniedException ex) {
    String message =
        ex.getMessage() != null && !ex.getMessage().isBlank()
            ? ex.getMessage()
            : DEFAULT_FORBIDDEN_MESSAGE;
    return ExceptionPayload.builder().status(HttpStatus.FORBIDDEN.value()).message(message).build();
  }

  @ExceptionHandler(DisabledException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ExceptionPayload handleDisabledException(DisabledException ex) {
    return ExceptionPayload.builder()
        .status(HttpStatus.FORBIDDEN.value())
        .message(AuthMessages.USER_DISABLED)
        .build();
  }

  private static ExceptionPayload validationErrorPayload(Map<String, String> fieldErrors) {
    return ExceptionPayload.builder()
        .status(HttpStatus.BAD_REQUEST.value())
        .message(VALIDATION_ERROR_MESSAGE)
        .fieldErrors(fieldErrors)
        .build();
  }

  private static String lastPropertyName(Path propertyPath) {
    String name = propertyPath.toString();
    for (Path.Node node : propertyPath) {
      if (node instanceof Path.PropertyNode propertyNode) {
        name = propertyNode.getName();
      }
    }
    return name;
  }
}
