package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ConfirmAccountEmailRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountEmailChangeUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email-changes")
@RequiredArgsConstructor
public class AccountEmailConfirmationController {
  private final AccountEmailChangeUseCase emailChanges;

  @PostMapping(value = "/confirm", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@Valid @RequestBody final ConfirmAccountEmailRequest request) {
    emailChanges.confirm(request.token());
  }
}
