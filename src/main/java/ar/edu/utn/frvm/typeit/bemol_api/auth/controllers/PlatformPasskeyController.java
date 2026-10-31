package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyAuthenticationOptionsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyRegistrationOptionsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyRegistrationVerifyRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasskeyRenameRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformPasskeyReauthRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformPasskeySetupRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyListResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyManagementUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/auth/passkeys")
@RequiredArgsConstructor
public class PlatformPasskeyController {
  private final PlatformPasskeyUseCase useCase;
  private final PlatformPasskeyManagementUseCase management;

  @PostMapping(version = Version.V1, path = "/authentication/options")
  public PasskeyAuthenticationOptionsResponse authenticationOptions(
      @Valid @RequestBody final PasskeyAuthenticationOptionsRequest request) {
    return useCase.authenticationOptions(request.loginAttemptId());
  }

  @PostMapping(version = Version.V1, path = "/authentication/verify")
  public PlatformAuthResponse authenticate(
      @Valid @RequestBody final PasskeyRegistrationVerifyRequest request,
      final HttpServletRequest httpRequest) {
    return useCase.authenticate(request.ceremonyId(), request.credential(), httpRequest);
  }

  @PostMapping(version = Version.V1, path = "/setup/options")
  public PasskeyRegistrationOptionsResponse setupOptions(
      @Valid @RequestBody final PlatformPasskeySetupRequest request) {
    return useCase.setupOptions(request.loginAttemptId(), request.code(), request.label());
  }

  @PostMapping(version = Version.V1, path = "/setup/verify")
  public PlatformAuthResponse setup(
      @Valid @RequestBody final PasskeyRegistrationVerifyRequest request,
      final HttpServletRequest httpRequest) {
    return useCase.setup(request.ceremonyId(), request.credential(), httpRequest);
  }

  @GetMapping(version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PasskeyListResponse list(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean used,
      @SortDefault(sort = "createdAt", direction = Sort.Direction.ASC) final Sort sort) {
    return management.list(principal.platformAccountId(), search, used, sort);
  }

  @PostMapping(version = Version.V1, path = "/reauth/options")
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PasskeyAuthenticationOptionsResponse reauthOptions(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final PlatformPasskeyReauthRequest request) {
    return management.reauthOptions(principal, request.target());
  }

  @PostMapping(version = Version.V1, path = "/reauth/verify")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public void reauthenticate(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final PasskeyRegistrationVerifyRequest request) {
    management.reauthenticate(principal, request.ceremonyId(), request.credential());
  }

  @PostMapping(version = Version.V1, path = "/registration/options")
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PasskeyRegistrationOptionsResponse registrationOptions(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final PasskeyRegistrationOptionsRequest request) {
    return management.registrationOptions(principal, request.label());
  }

  @PostMapping(version = Version.V1, path = "/registration/verify")
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PasskeyResponse register(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final PasskeyRegistrationVerifyRequest request) {
    return management.register(principal, request.ceremonyId(), request.credential());
  }

  @PatchMapping(version = Version.V1, path = "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public void rename(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @PathVariable final UUID id,
      @Valid @RequestBody final PasskeyRenameRequest request) {
    management.rename(principal.platformAccountId(), id, request.label());
  }

  @DeleteMapping(version = Version.V1, path = "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public void revoke(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @PathVariable final UUID id) {
    management.revoke(principal, id);
  }
}
