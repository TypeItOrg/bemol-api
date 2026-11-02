package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ChangePlatformEmailRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ChangePlatformPasswordRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ConfirmAccountEmailRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdateOwnPlatformProfileRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformAccountManagementUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth/account")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequiredArgsConstructor
public class PlatformAccountController {
  private final PlatformAccountManagementUseCase accounts;

  @PutMapping(path = "/profile", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateProfile(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final UpdateOwnPlatformProfileRequest request) {
    accounts.updateProfile(principal, request);
  }

  @PostMapping(path = "/password", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final ChangePlatformPasswordRequest request) {
    accounts.changePassword(principal, request);
  }

  @PostMapping(path = "/email-changes", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestEmailChange(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final ChangePlatformEmailRequest request) {
    accounts.requestEmailChange(principal, request.email());
  }

  @PostMapping(path = "/email-changes/confirm", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmEmailChange(
      @AuthenticationPrincipal final JwtAuthenticatedPlatformAccount principal,
      @Valid @RequestBody final ConfirmAccountEmailRequest request) {
    accounts.confirmEmailChange(principal, request.token());
  }
}
