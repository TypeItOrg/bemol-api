package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.AutoLoginProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AutoLoginUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/login")
@Profile(AutoLoginProperties.NON_PRODUCTION_PROFILE)
@ConditionalOnProperty(prefix = "app.auto-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AutoLoginController {
  private final AutoLoginUseCase useCase;

  @PostMapping(version = Version.V1, path = "/auto")
  public AuthResponse login(final HttpServletRequest request) {
    return useCase.execute(request);
  }
}
