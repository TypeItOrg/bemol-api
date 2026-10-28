package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ConfirmEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ResendEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalEmailVerificationUseCase;
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
@RequestMapping("/auth/email-verification")
@RequiredArgsConstructor
public class EmailVerificationController {
  private final InstitutionalEmailVerificationUseCase verification;

  @PostMapping(path = "/confirm", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@Valid @RequestBody final ConfirmEmailVerificationRequest request) {
    verification.confirm(request);
  }

  @PostMapping(path = "/resend", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resend(@Valid @RequestBody final ResendEmailVerificationRequest request) {
    verification.resend(request);
  }
}
