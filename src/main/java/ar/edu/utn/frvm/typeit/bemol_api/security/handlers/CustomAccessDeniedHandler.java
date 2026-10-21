package ar.edu.utn.frvm.typeit.bemol_api.security.handlers;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  private final SecurityErrorResponseWriter responseWriter;

  @Override
  public void handle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull AccessDeniedException accessDeniedException)
      throws IOException, ServletException {

    String message =
        accessDeniedException.getMessage() != null
            ? accessDeniedException.getMessage()
            : DEFAULT_FORBIDDEN_MESSAGE;

    responseWriter.write(response, HttpStatus.FORBIDDEN, message);
  }
}
