package ar.edu.utn.frvm.typeit.bemol_api.security.handlers;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_UNAUTHORIZED_MESSAGE;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final SecurityErrorResponseWriter responseWriter;

  @Override
  public void commence(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull AuthenticationException authException)
      throws IOException, ServletException {

    String message =
        authException.getMessage() != null
            ? authException.getMessage()
            : DEFAULT_UNAUTHORIZED_MESSAGE;

    responseWriter.write(response, HttpStatus.UNAUTHORIZED, message);
  }
}
