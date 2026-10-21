package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.ReplacePersonRolesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.SystemRoleListResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListPersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListSystemRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ReplacePersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.RevokePersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
public class RoleController {

  private final InstitutionalCallerGuard institutionalCallerGuard;
  private final ListPersonRolesUseCase listPersonRolesUseCase;
  private final AssignPersonRoleUseCase assignPersonRoleUseCase;
  private final RevokePersonRoleUseCase revokePersonRoleUseCase;
  private final ReplacePersonRolesUseCase replacePersonRolesUseCase;
  private final AuthorizationService authorizationService;
  private final ListSystemRolesUseCase listSystemRolesUseCase;

  @GetMapping(value = "/institutions/{institutionId}/people/{personId}/roles", version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresAnyPermission({
    PermissionCode.INSTITUTION_ROLE_ASSIGN,
    PermissionCode.INSTITUTION_ROLE_REVOKE
  })
  public List<PersonRoleResponse> listPersonRoles(
      @PathVariable UUID institutionId,
      @PathVariable UUID personId,
      Authentication authentication) {
    return listPersonRolesUseCase.execute(institutionId, personId);
  }

  @PostMapping(
      value = "/institutions/{institutionId}/people/{personId}/roles",
      version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN)
  public PersonRoleResponse assignPersonRole(
      @PathVariable UUID institutionId,
      @PathVariable UUID personId,
      @Valid @RequestBody AssignRoleRequest request,
      Authentication authentication) {
    return assignPersonRoleUseCase.execute(institutionId, personId, request, false);
  }

  @DeleteMapping(
      value = "/institutions/{institutionId}/people/{personId}/roles/{roleId}",
      version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_REVOKE)
  public void revokePersonRole(
      @PathVariable UUID institutionId,
      @PathVariable UUID personId,
      @PathVariable UUID roleId,
      Authentication authentication) {
    revokePersonRoleUseCase.execute(institutionId, personId, roleId, false);
  }

  @PutMapping(value = "/institutions/{institutionId}/people/{personId}/roles", version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresAnyPermission({
    PermissionCode.INSTITUTION_ROLE_ASSIGN,
    PermissionCode.INSTITUTION_ROLE_REVOKE
  })
  public List<PersonRoleResponse> replacePersonRoles(
      @PathVariable UUID institutionId,
      @PathVariable UUID personId,
      @Valid @RequestBody ReplacePersonRolesRequest request,
      Authentication authentication) {
    return replacePersonRolesUseCase.execute(
        institutionId,
        personId,
        request,
        false,
        authorizationService.resolvePermissions(authentication));
  }

  @GetMapping(value = "/roles/system", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_ASSIGN)
  public SystemRoleListResponse listSystemRoles(Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    return SystemRoleListResponse.from(listSystemRolesUseCase.execute());
  }
}
