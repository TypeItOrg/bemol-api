package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.CreatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdatePlatformAccountStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountAdminResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.CreatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.GetPlatformAccountAdminUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ListPlatformAccountsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.UpdatePlatformAccountStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.UpdatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class PlatformAccountAdminController {

  private final ListPlatformAccountsUseCase listPlatformAccountsUseCase;
  private final GetPlatformAccountAdminUseCase getPlatformAccountAdminUseCase;
  private final CreatePlatformAccountUseCase createPlatformAccountUseCase;
  private final UpdatePlatformAccountUseCase updatePlatformAccountUseCase;
  private final UpdatePlatformAccountStatusUseCase updatePlatformAccountStatusUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PaginatedResponse<PlatformAccountAdminResponse> list(
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean enabled,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return listPlatformAccountsUseCase.execute(search, enabled, pageable);
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PlatformAccountAdminResponse create(
      @Valid @RequestBody final CreatePlatformAccountRequest request) {
    return createPlatformAccountUseCase.execute(request);
  }

  @GetMapping(value = "/{id}", version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PlatformAccountAdminResponse get(@PathVariable final UUID id) {
    return getPlatformAccountAdminUseCase.execute(id);
  }

  @PutMapping(value = "/{id}", version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PlatformAccountAdminResponse update(
      @PathVariable final UUID id, @Valid @RequestBody final UpdatePlatformAccountRequest request) {
    return updatePlatformAccountUseCase.execute(id, request);
  }

  @PatchMapping(value = "/{id}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public void updateStatus(
      @PathVariable final UUID id,
      @Valid @RequestBody final UpdatePlatformAccountStatusRequest request,
      final Authentication authentication) {
    final var principal =
        (JwtAuthenticatedPlatformAccount) requireNonNull(authentication.getPrincipal());
    updatePlatformAccountStatusUseCase.execute(
        id, principal.platformAccountId(), request.enabled());
  }
}
