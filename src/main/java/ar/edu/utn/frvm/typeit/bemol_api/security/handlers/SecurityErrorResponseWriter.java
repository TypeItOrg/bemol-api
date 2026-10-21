package ar.edu.utn.frvm.typeit.bemol_api.security.handlers;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ExceptionPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public void write(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    ExceptionPayload payload =
        ExceptionPayload.builder().status(status.value()).message(message).build();

    objectMapper.writeValue(response.getWriter(), payload);
  }
}
