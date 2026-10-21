package ar.edu.utn.frvm.typeit.bemol_api.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

  private RequestLoggingFilter filter;
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    filter = new RequestLoggingFilter();
    logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
  }

  @Test
  @DisplayName("Generates new requestId when X-Request-Id header is absent")
  void shouldGenerateNewRequestIdWhenHeaderIsAbsent() throws ServletException, IOException {
    // Given
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain = new MockFilterChain();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    final String responseHeader = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    assertThat(responseHeader).isNotNull().isNotBlank();
    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
  }

  @Test
  @DisplayName("Propagates valid incoming X-Request-Id header")
  void shouldPropagateValidIncomingRequestId() throws ServletException, IOException {
    // Given
    final String validRequestId = "12345678-1234-1234-1234-123456789abc";
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, validRequestId);

    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain = new MockFilterChain();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    final String responseHeader = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    assertThat(responseHeader).isEqualTo(validRequestId);
    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
  }

  @Test
  @DisplayName("Discards invalid incoming X-Request-Id and generates new one")
  void shouldDiscardInvalidIncomingRequestId() throws ServletException, IOException {
    // Given
    final String invalidRequestId = "invalid req id with spaces and <script>";
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, invalidRequestId);

    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain = new MockFilterChain();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    final String responseHeader = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    assertThat(responseHeader).isNotNull().isNotEqualTo(invalidRequestId);
    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
  }

  @Test
  @DisplayName("Populates MDC with requestId during filter execution and clears it afterwards")
  void shouldPopulateAndClearMdcContext() throws ServletException, IOException {
    // Given
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final String[] capturedMdcId = new String[1];

    final MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              final jakarta.servlet.ServletRequest req, final jakarta.servlet.ServletResponse res) {
            capturedMdcId[0] = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY);
          }
        };

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    final String responseHeader = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    assertThat(capturedMdcId[0]).isNotNull().isEqualTo(responseHeader);
    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
  }

  @Test
  @DisplayName("Clears MDC even when filter chain throws an exception")
  void shouldClearMdcOnException() {
    // Given
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              final jakarta.servlet.ServletRequest req, final jakarta.servlet.ServletResponse res)
              throws ServletException {
            throw new ServletException("Simulated filter chain failure");
          }
        };

    // When / Then
    assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
        .isInstanceOf(ServletException.class);

    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
  }

  @Test
  @DisplayName("Logs status 500 and WARN level when filter chain throws an unhandled exception")
  void shouldLogStatus500WhenFilterChainThrowsException() {
    // Given
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              final jakarta.servlet.ServletRequest req, final jakarta.servlet.ServletResponse res)
              throws ServletException {
            throw new ServletException("Simulated unhandled exception");
          }
        };

    // When
    assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
        .isInstanceOf(ServletException.class);

    // Then
    assertThat(listAppender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains("status: 500");
            });
  }

  @Test
  @DisplayName("Handles successful health check path without log noise")
  void shouldHandleHealthCheckPath() throws ServletException, IOException {
    // Given
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/actuator/health/readiness");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain = new MockFilterChain();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    final String responseHeader = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    assertThat(responseHeader).isNotNull();
    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
    assertThat(listAppender.list).isEmpty();
  }

  @Test
  @DisplayName("Logs failed health check path with status 503 as WARN level")
  void shouldLogFailedHealthCheckPath() throws ServletException, IOException {
    // Given
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/actuator/health/readiness");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(503);
    final MockFilterChain filterChain = new MockFilterChain();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    final String responseHeader = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    assertThat(responseHeader).isNotNull();
    assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();

    assertThat(listAppender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains("status: 503");
              assertThat(event.getFormattedMessage()).contains("/actuator/health/readiness");
            });
  }
}
