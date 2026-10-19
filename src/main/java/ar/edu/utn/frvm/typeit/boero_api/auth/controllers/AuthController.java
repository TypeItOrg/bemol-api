package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.IdentifyLoginRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.PasswordLoginRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.PasswordRecoveryRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ResetPasswordRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.SelectActiveIdentityRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.ActiveSessionResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.IdentifyLoginResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserRegisteredResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ActiveIdentityService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.GetActiveSessionsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.GetCurrentUserUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IdentifyLoginUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.LogoutUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PasswordLoginWithAttemptUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RefreshTokenUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RegisterUserUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RequestInstitutionalPasswordRecoveryUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ResetInstitutionalPasswordUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.utils.HeaderUtils;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final GetActiveSessionsUseCase getActiveSessionsUseCase;
  private final GetCurrentUserUseCase getCurrentUserUseCase;
  private final ActiveIdentityService activeIdentityService;
  private final IdentifyLoginUseCase identifyLoginUseCase;
  private final LogoutUseCase logoutUseCase;
  private final PasswordLoginWithAttemptUseCase passwordLoginWithAttemptUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final RegisterUserUseCase registerUserUseCase;
  private final RequestInstitutionalPasswordRecoveryUseCase
      requestInstitutionalPasswordRecoveryUseCase;
  private final ResetInstitutionalPasswordUseCase resetInstitutionalPasswordUseCase;
  private final InstitutionalCallerGuard institutionalCallerGuard;

  @PostMapping(version = Version.V1, path = "/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserRegisteredResponse register(@Valid @RequestBody RegisterRequest request) {
    return registerUserUseCase.execute(request);
  }

  @PostMapping(version = Version.V1, path = "/login/identify")
  public IdentifyLoginResponse identify(@Valid @RequestBody IdentifyLoginRequest request) {
    return identifyLoginUseCase.execute(request);
  }

  @PostMapping(version = Version.V1, path = "/login/password")
  public AuthResponse loginWithPassword(
      @Valid @RequestBody PasswordLoginRequest request, HttpServletRequest httpRequest) {
    return passwordLoginWithAttemptUseCase.execute(request, httpRequest);
  }

  @PostMapping(version = Version.V1, path = "/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return refreshTokenUseCase.execute(request);
  }

  @PostMapping(version = Version.V1, path = "/password-recovery")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestPasswordRecovery(@Valid @RequestBody final PasswordRecoveryRequest request) {
    requestInstitutionalPasswordRecoveryUseCase.execute(request);
  }

  @PostMapping(version = Version.V1, path = "/password-recovery/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody final ResetPasswordRequest request) {
    resetInstitutionalPasswordUseCase.execute(request);
  }

  @PostMapping(version = Version.V1, path = "/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    String token = HeaderUtils.bearerValue(authorization);
    logoutUseCase.execute(principal, token);
  }

  @GetMapping(version = Version.V1, path = "/sessions")
  public PaginatedResponse<ActiveSessionResponse> sessions(
      Authentication authentication,
      @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return getActiveSessionsUseCase.execute(principal, pageable);
  }

  @PostMapping(version = Version.V1, path = "/active-identity")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void selectIdentity(
      @Valid @RequestBody SelectActiveIdentityRequest request, Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    activeIdentityService.select(
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal()), request.personId());
  }

  @GetMapping(version = Version.V1, path = "/session")
  public UserResponse session(Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    return getCurrentUserUseCase.execute(
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal()));
  }

  @GetMapping(version = Version.V1, path = "/me")
  public UserResponse me(Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return getCurrentUserUseCase.execute(principal);
  }
}
