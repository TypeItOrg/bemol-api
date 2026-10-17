package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ActivateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.ActivatePersonOwnAccessUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/own-access")
@RequiredArgsConstructor
public class OwnAccessActivationController {
  private final ActivatePersonOwnAccessUseCase access;

  @PostMapping(value = "/activate", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void activate(@Valid @RequestBody ActivateOwnAccessRequest request) {
    access.execute(request);
  }
}
