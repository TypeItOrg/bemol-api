package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetPersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/person")
@RequiredArgsConstructor
public class PersonController {

  private final GetPersonUseCase getPersonUseCase;
  private final UpdatePersonUseCase updatePersonUseCase;
  private final InstitutionalCallerGuard institutionalCallerGuard;

  @GetMapping(value = "/me", version = Version.V1)
  public PersonResponse me(Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return getPersonUseCase.execute(principal);
  }

  @PutMapping(value = "/me", version = Version.V1)
  public PersonResponse updateMe(
      Authentication authentication, @Valid @RequestBody UpdatePersonRequest request) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    JwtAuthenticatedUser principal =
        (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return updatePersonUseCase.execute(principal, request);
  }
}
