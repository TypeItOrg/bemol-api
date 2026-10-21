package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.DiscoverablePasskeyOptionsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.DiscoverablePasskeyVerifyRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyAuthenticationOptionsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyAuthenticationVerifyRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyRegistrationOptionsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyRegistrationVerifyRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyRenameRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ReAuthenticateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyListResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.DiscoverablePasskeyLoginUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ListPasskeysUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ReAuthenticateUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.RenamePasskeyUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.RequestPasskeyAuthenticationOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.RequestPasskeyRegistrationOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.RevokePasskeyUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.VerifyPasskeyAuthenticationUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.VerifyPasskeyRegistrationUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasskeyController {

  private final ListPasskeysUseCase listPasskeysUseCase;
  private final RequestPasskeyRegistrationOptionsUseCase requestRegistrationOptionsUseCase;
  private final VerifyPasskeyRegistrationUseCase verifyPasskeyRegistrationUseCase;
  private final RenamePasskeyUseCase renamePasskeyUseCase;
  private final RevokePasskeyUseCase revokePasskeyUseCase;
  private final RequestPasskeyAuthenticationOptionsUseCase requestAuthenticationOptionsUseCase;
  private final VerifyPasskeyAuthenticationUseCase verifyPasskeyAuthenticationUseCase;
  private final ReAuthenticateUseCase reAuthenticateUseCase;
  private final InstitutionalCallerGuard institutionalCallerGuard;
  private final WebAuthnProperties webAuthnProperties;
  private final DiscoverablePasskeyLoginUseCase discoverablePasskeyLoginUseCase;

  @PostMapping(version = Version.V1, path = "/passkeys/authentication/discoverable/options")
  public PasskeyAuthenticationOptionsResponse discoverableOptions(
      @Valid @RequestBody final DiscoverablePasskeyOptionsRequest request) {
    return discoverablePasskeyLoginUseCase.options(request.institutionId());
  }

  @PostMapping(version = Version.V1, path = "/passkeys/authentication/discoverable/verify")
  public AuthResponse discoverableVerify(
      @Valid @RequestBody final DiscoverablePasskeyVerifyRequest request,
      final HttpServletRequest httpRequest) {
    return discoverablePasskeyLoginUseCase.verify(
        request.ceremonyId(), request.credential(), request.rememberMe(), httpRequest);
  }

  @PostMapping(version = Version.V1, path = "/passkeys/authentication/options")
  public PasskeyAuthenticationOptionsResponse authenticationOptions(
      @Valid @RequestBody final PasskeyAuthenticationOptionsRequest request) {
    return requestAuthenticationOptionsUseCase.execute(request.loginAttemptId());
  }

  @PostMapping(version = Version.V1, path = "/passkeys/authentication/verify")
  public AuthResponse authenticationVerify(
      @Valid @RequestBody final PasskeyAuthenticationVerifyRequest request,
      final HttpServletRequest httpRequest) {
    return verifyPasskeyAuthenticationUseCase.execute(
        request.loginAttemptId(),
        request.ceremonyId(),
        request.credential(),
        request.rememberMe(),
        httpRequest);
  }

  @GetMapping(version = Version.V1, path = "/passkeys")
  public PasskeyListResponse list(final Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return new PasskeyListResponse(
        listPasskeysUseCase.execute(principal), webAuthnProperties.maxPasskeys());
  }

  @PostMapping(version = Version.V1, path = "/passkeys/registration/options")
  public PasskeyRegistrationOptionsResponse registrationOptions(
      final Authentication authentication,
      @Valid @RequestBody final PasskeyRegistrationOptionsRequest request) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return requestRegistrationOptionsUseCase.execute(principal, request.label());
  }

  @PostMapping(version = Version.V1, path = "/passkeys/registration/verify")
  @ResponseStatus(HttpStatus.CREATED)
  public PasskeyResponse registrationVerify(
      final Authentication authentication,
      @Valid @RequestBody final PasskeyRegistrationVerifyRequest request) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return verifyPasskeyRegistrationUseCase.execute(
        principal, request.ceremonyId(), request.credential());
  }

  @PatchMapping(version = Version.V1, path = "/passkeys/{id}")
  public PasskeyResponse rename(
      final Authentication authentication,
      @PathVariable("id") final UUID passkeyId,
      @Valid @RequestBody final PasskeyRenameRequest request) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return renamePasskeyUseCase.execute(principal, passkeyId, request.label());
  }

  @DeleteMapping(version = Version.V1, path = "/passkeys/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(
      final Authentication authentication, @PathVariable("id") final UUID passkeyId) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    revokePasskeyUseCase.execute(principal, passkeyId);
  }

  @PostMapping(version = Version.V1, path = "/re-authenticate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reAuthenticate(
      final Authentication authentication,
      @Valid @RequestBody final ReAuthenticateRequest request) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    reAuthenticateUseCase.execute(principal, request.password());
  }
}
