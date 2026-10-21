package ar.edu.utn.frvm.typeit.bemol_api.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@NullMarked
public class RequestLoggingFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String MDC_REQUEST_ID_KEY = "requestId";

  private static final Pattern VALID_REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{8,36}$");

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final String incomingRequestId = request.getHeader(REQUEST_ID_HEADER);
    final String requestId =
        isValidRequestId(incomingRequestId) ? incomingRequestId : UUID.randomUUID().toString();

    MDC.put(MDC_REQUEST_ID_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    final long startTime = System.nanoTime();
    Throwable chainException = null;

    try {
      filterChain.doFilter(request, response);
    } catch (final ServletException | IOException | RuntimeException ex) {
      chainException = ex;
      throw ex;
    } finally {
      final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
      int status = response.getStatus();
      if (chainException != null && status < 500) {
        status = 500;
      }
      logRequestSummary(request, status, durationMs);
      MDC.remove(MDC_REQUEST_ID_KEY);
    }
  }

  private boolean isValidRequestId(final String header) {
    if (header == null || header.isBlank()) {
      return false;
    }
    return VALID_REQUEST_ID_PATTERN.matcher(header).matches();
  }

  private void logRequestSummary(
      final HttpServletRequest request, final int status, final long durationMs) {
    final String path = request.getRequestURI();
    final String method = request.getMethod();

    if (isHealthCheckPath(path) && status < 400) {
      if (log.isDebugEnabled()) {
        log.debug(
            "[HTTP] Request completed, method: {}, path: {}, status: {}, durationMs: {}",
            method,
            path,
            status,
            durationMs);
      }
      return;
    }

    if (status < 400) {
      log.debug(
          "[HTTP] Request completed, method: {}, path: {}, status: {}, durationMs: {}",
          method,
          path,
          status,
          durationMs);
    } else if (status < 500) {
      log.info(
          "[HTTP] Request completed, method: {}, path: {}, status: {}, durationMs: {}",
          method,
          path,
          status,
          durationMs);
    } else {
      log.warn(
          "[HTTP] Request completed, method: {}, path: {}, status: {}, durationMs: {}",
          method,
          path,
          status,
          durationMs);
    }
  }

  private boolean isHealthCheckPath(final String path) {
    return path != null && path.startsWith("/actuator/health");
  }
}
