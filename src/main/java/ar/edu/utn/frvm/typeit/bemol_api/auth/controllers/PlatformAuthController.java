package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;
import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.GetCurrentPlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformLoginUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformLogoutUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformRefreshTokenUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.utils.HeaderUtils;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

  private final PlatformLoginUseCase platformLoginUseCase;
  private final PlatformRefreshTokenUseCase platformRefreshTokenUseCase;
  private final PlatformLogoutUseCase platformLogoutUseCase;
  private final GetCurrentPlatformAccountUseCase getCurrentPlatformAccountUseCase;

  @PostMapping(version = Version.V1, path = "/login")
  public PlatformAuthResponse login(
      @Valid @RequestBody PlatformLoginRequest request, HttpServletRequest httpRequest) {
    return platformLoginUseCase.execute(request, httpRequest);
  }

  @PostMapping(version = Version.V1, path = "/refresh")
  public PlatformAuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return platformRefreshTokenUseCase.execute(request);
  }

  @GetMapping(version = Version.V1, path = "/me")
  public PlatformAccountResponse me(Authentication authentication) {
    JwtAuthenticatedPlatformAccount principal =
        (JwtAuthenticatedPlatformAccount) requireNonNull(authentication.getPrincipal());
    return getCurrentPlatformAccountUseCase.execute(principal);
  }

  @PostMapping(version = Version.V1, path = "/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount principal)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    platformLogoutUseCase.execute(principal, HeaderUtils.bearerValue(authorization));
  }
}
