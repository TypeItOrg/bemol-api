package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.AccountEmailRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.AccountRecoveryRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountEmailChangeUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalAccountManagementUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.OwnAccessResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.UpdateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.InvitePersonAccessRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess
@RestController
@RequestMapping("/institutions/{institutionId}/people/{personId}")
@RequiredArgsConstructor
public class AccountAccessController {
  private final InstitutionalAccountManagementUseCase accounts;
  private final AccountEmailChangeUseCase emailChanges;
  private final ar.edu.utn.frvm.typeit.bemol_api.auth.services.GrantPersonAutonomyUseCase
      grantAutonomy;

  @GetMapping(value = "/access", version = Version.V1)
  public OwnAccessResponse status(
      @PathVariable final UUID institutionId, @PathVariable final UUID personId) {
    return accounts.status(institutionId, personId);
  }

  @PostMapping(value = "/access-invitations", version = Version.V1)
  public OwnAccessResponse invite(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final InvitePersonAccessRequest request) {
    return accounts.invite(institutionId, personId, request.email());
  }

  @DeleteMapping(value = "/access-invitations", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable final UUID institutionId, @PathVariable final UUID personId) {
    accounts.cancel(institutionId, personId);
  }

  @PatchMapping(value = "/own-access", version = Version.V1)
  public OwnAccessResponse enable(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final UpdateOwnAccessRequest request) {
    return accounts.enable(
        institutionId, personId, java.util.Objects.requireNonNull(request.enabled()));
  }

  @PostMapping(value = "/password-recovery", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void recover(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final AccountRecoveryRequest request) {
    accounts.recover(institutionId, personId, request.currentPassword());
  }

  @PostMapping(value = "/email-changes", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeEmail(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final AccountEmailRequest request) {
    emailChanges.request(institutionId, personId, request.email(), request.currentPassword());
  }

  @PostMapping(value = "/autonomy", version = Version.V1)
  public OwnAccessResponse autonomy(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final AccountEmailRequest request) {
    return grantAutonomy.execute(
        institutionId, personId, request.email(), request.currentPassword());
  }
}
