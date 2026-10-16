package ar.edu.utn.frvm.typeit.boero_api.authorization.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionGroup;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionPermissionGroupResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionRoleRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
public class InstitutionRoleController {

  private final InstitutionRoleManagementService roleService;
  private final AuthorizationService authorizationService;

  @GetMapping(value = "/institutions/{institutionId}/roles", version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresAnyPermission({
    PermissionCode.INSTITUTION_ROLE_READ,
    PermissionCode.INSTITUTION_PERSON_READ_ANY
  })
  public PaginatedResponse<InstitutionRoleResponse> list(
      @PathVariable UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) String search,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
      Authentication authentication) {
    return roleService.list(institutionId, search, pageable);
  }

  @GetMapping(value = "/institutions/{institutionId}/roles/{roleId}", version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_READ)
  public InstitutionRoleResponse get(
      @PathVariable UUID institutionId, @PathVariable UUID roleId, Authentication authentication) {
    var principal = (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return roleService.get(institutionId, roleId, true, principal.personId());
  }

  @GetMapping(value = "/institutions/{institutionId}/permissions", version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_READ)
  public List<InstitutionPermissionGroupResponse> permissions(
      @PathVariable UUID institutionId, Authentication authentication) {
    var actorPermissions = authorizationService.resolvePermissions(authentication);
    return Arrays.stream(PermissionGroup.values())
        .map(group -> InstitutionPermissionGroupResponse.from(group, actorPermissions))
        .toList();
  }

  @PostMapping(value = "/institutions/{institutionId}/roles", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_CREATE)
  public InstitutionRoleResponse create(
      @PathVariable UUID institutionId,
      @Valid @RequestBody InstitutionRoleRequest request,
      Authentication authentication) {
    return roleService.create(
        institutionId, request, authorizationService.resolvePermissions(authentication));
  }

  @PutMapping(value = "/institutions/{institutionId}/roles/{roleId}", version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_UPDATE)
  public InstitutionRoleResponse update(
      @PathVariable UUID institutionId,
      @PathVariable UUID roleId,
      @Valid @RequestBody InstitutionRoleRequest request,
      Authentication authentication) {
    var principal = (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return roleService.update(
        institutionId,
        roleId,
        request,
        principal.personId(),
        authorizationService.resolvePermissions(authentication));
  }

  @DeleteMapping(value = "/institutions/{institutionId}/roles/{roleId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresInstitutionAccess
  @RequiresPermission(PermissionCode.INSTITUTION_ROLE_DELETE)
  public void delete(
      @PathVariable UUID institutionId, @PathVariable UUID roleId, Authentication authentication) {
    roleService.delete(institutionId, roleId);
  }
}
