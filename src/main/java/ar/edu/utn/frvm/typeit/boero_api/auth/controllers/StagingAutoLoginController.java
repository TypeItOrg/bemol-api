package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.StagingAutoLoginProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.StagingAutoLoginUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/login")
@Profile(StagingAutoLoginProperties.PROFILE)
@ConditionalOnProperty(prefix = "app.auto-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StagingAutoLoginController {
  private final StagingAutoLoginUseCase useCase;

  @PostMapping(version = Version.V1, path = "/staging")
  public AuthResponse login(final HttpServletRequest request) {
    return useCase.execute(request);
  }
}
