package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.ReplacePersonRolesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.SystemRoleListResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.BootstrapInstitutionalAuthorityUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListPersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListSystemRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ReplacePersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.RevokePersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
public class AdminRoleController {

  private final ListPersonRolesUseCase listPersonRolesUseCase;
  private final AssignPersonRoleUseCase assignPersonRoleUseCase;
  private final RevokePersonRoleUseCase revokePersonRoleUseCase;
  private final ReplacePersonRolesUseCase replacePersonRolesUseCase;
  private final ListSystemRolesUseCase listSystemRolesUseCase;
  private final BootstrapInstitutionalAuthorityUseCase bootstrapInstitutionalAuthorityUseCase;
  private final InstitutionRoleManagementService institutionRoleManagementService;

  @GetMapping(value = "/admin/institutions/{institutionId}/roles", version = Version.V1)
  public List<InstitutionRoleResponse> listInstitutionRoles(
      @PathVariable final UUID institutionId) {
    return institutionRoleManagementService.list(institutionId, true);
  }

  @GetMapping(
      value = "/admin/institutions/{institutionId}/people/{personId}/roles",
      version = Version.V1)
  public List<PersonRoleResponse> listPersonRoles(
      @PathVariable final UUID institutionId, @PathVariable final UUID personId) {
    return listPersonRolesUseCase.execute(institutionId, personId);
  }

  @PostMapping(
      value = "/admin/institutions/{institutionId}/people/{personId}/roles",
      version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public PersonRoleResponse assignPersonRole(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final AssignRoleRequest request) {
    return assignPersonRoleUseCase.execute(institutionId, personId, request, true);
  }

  @DeleteMapping(
      value = "/admin/institutions/{institutionId}/people/{personId}/roles/{roleId}",
      version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokePersonRole(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @PathVariable final UUID roleId) {
    revokePersonRoleUseCase.execute(institutionId, personId, roleId, true);
  }

  @PutMapping(
      value = "/admin/institutions/{institutionId}/people/{personId}/roles",
      version = Version.V1)
  public List<PersonRoleResponse> replacePersonRoles(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final ReplacePersonRolesRequest request) {
    return replacePersonRolesUseCase.execute(institutionId, personId, request, true, Set.of());
  }

  @GetMapping(value = "/admin/roles/system", version = Version.V1)
  public SystemRoleListResponse listSystemRoles() {
    return SystemRoleListResponse.from(listSystemRolesUseCase.execute());
  }

  @PostMapping(
      value = "/admin/institutions/{institutionId}/authority/{personId}",
      version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public PersonRoleResponse bootstrapInstitutionalAuthority(
      @PathVariable final UUID institutionId, @PathVariable final UUID personId) {
    return bootstrapInstitutionalAuthorityUseCase.execute(institutionId, personId);
  }
}
