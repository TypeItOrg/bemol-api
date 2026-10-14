package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.EmailVerificationCooldownException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticationFilter;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.InstitutionalEmailVerificationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.InstitutionalHostContext;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.security.config.SecurityConfig;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.CustomAccessDeniedHandler;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.CustomAuthenticationEntryPoint;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

@WebMvcTest(EmailVerificationController.class)
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  CustomAuthenticationEntryPoint.class,
  CustomAccessDeniedHandler.class,
  SecurityErrorResponseWriter.class,
  GlobalExceptionHandler.class
})
class EmailVerificationControllerWebMvcTest {
  @TestConfiguration
  static class Config {
    @Bean
    com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
      return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Bean
    PathMatcher pathMatcher() {
      return new AntPathMatcher();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean InstitutionalEmailVerificationUseCase verification;
  @MockitoBean InstitutionalHostContext institutionalHostContext;
  @MockitoBean JwtService jwt;
  @MockitoBean AuthorizationService authorization;
  @MockitoBean TokenBlacklistService blacklist;
  @MockitoBean IsSessionActiveUseCase sessions;
  @MockitoBean IsPlatformSessionActiveUseCase platformSessions;
  final String identity =
      "\"institutionId\":\"22222222-2222-2222-2222-222222222222\",\"documentNumber\":\"12345678\"";

  @Test
  void endpointsArePublicAndReturnNoContent() throws Exception {
    mvc.perform(
            post("/api/v1/auth/email-verification/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + identity + "}"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + "a".repeat(43) + "\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/auth/email-verification/change-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{"
                        + identity
                        + ",\"email\":\"correct@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void invalidPayloadDoesNotInvokeUseCase() throws Exception {
    mvc.perform(
            post("/api/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invalid\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/email-verification/change-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + identity + ",\"email\":\"bad\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(verification);
  }

  @Test
  void businessErrorsUseExistingExceptionPayload() throws Exception {
    doThrow(new InvalidEmailVerificationTokenException()).when(verification).confirm(any());
    mvc.perform(
            post("/api/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + "a".repeat(43) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
    doThrow(new EmailVerificationCooldownException()).when(verification).changeEmail(any());
    mvc.perform(
            post("/api/v1/auth/email-verification/change-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{"
                        + identity
                        + ",\"email\":\"correct@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isConflict());
  }
}
