package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("Should map AccessDeniedException to forbidden payload")
  void shouldMapAccessDeniedExceptionToForbiddenPayload() {
    ExceptionPayload payload =
        handler.handleAccessDeniedException(new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE));

    assertThat(payload.status()).isEqualTo(403);
    assertThat(payload.message()).isEqualTo(DEFAULT_FORBIDDEN_MESSAGE);
    assertThat(payload.fieldErrors()).isNull();
  }

  @Test
  @DisplayName("Should map application exceptions to their HTTP payload")
  void shouldMapApplicationExceptionToPayload() {
    ResponseEntity<ExceptionPayload> response =
        handler.handleApplicationException(new InvalidCredentialsException());

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(401);
    assertThat(response.getBody().message())
        .isEqualTo("Las credenciales proporcionadas son inválidas.");
  }

  @Test
  @DisplayName("Should map field conflicts to their field errors")
  void shouldMapFieldConflictExceptionToPayload() {
    final ResponseEntity<ExceptionPayload> response =
        handler.handleApplicationException(
            new FieldConflictException("slug", "Ya existe una institución con ese slug."));

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(409);
    assertThat(response.getBody().message()).isEqualTo("Ya existe una institución con ese slug.");
    assertThat(response.getBody().fieldErrors())
        .containsEntry("slug", "Ya existe una institución con ese slug.")
        .hasSize(1);
  }

  @Test
  @DisplayName("Should map HttpMessageNotReadableException to bad request payload")
  void shouldMapHttpMessageNotReadableExceptionToBadRequest() {
    ExceptionPayload payload =
        handler.handleHttpMessageNotReadable(
            new HttpMessageNotReadableException(
                "", org.mockito.Mockito.mock(org.springframework.http.HttpInputMessage.class)));

    assertThat(payload.status()).isEqualTo(400);
    assertThat(payload.message()).isEqualTo("El formato del cuerpo de la solicitud es inválido.");
    assertThat(payload.fieldErrors()).isNull();
  }
}
