package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PlatformRoleListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PlatformRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListPlatformRolesUseCase;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/admin")
@RequiredArgsConstructor
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
public class PlatformRoleController {

  private final ListPlatformRolesUseCase listPlatformRolesUseCase;
  private final InstitutionRoleManagementService roleService;

  @GetMapping(value = "/roles", version = Version.V1)
  public PaginatedResponse<PlatformRoleListItemResponse> list(
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) final Boolean system,
      @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listPlatformRolesUseCase.execute(search, institutionId, system, pageable);
  }

  @GetMapping(value = "/roles/{roleId}", version = Version.V1)
  public PlatformRoleResponse get(@PathVariable final UUID roleId) {
    return roleService.getAsPlatformAdmin(roleId);
  }

  @PostMapping(value = "/institutions/{institutionId}/roles", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public InstitutionRoleResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final InstitutionRoleRequest request) {
    return roleService.createAsPlatformAdmin(institutionId, request);
  }

  @PutMapping(value = "/institutions/{institutionId}/roles/{roleId}", version = Version.V1)
  public InstitutionRoleResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID roleId,
      @Valid @RequestBody final InstitutionRoleRequest request) {
    return roleService.updateAsPlatformAdmin(institutionId, roleId, request);
  }

  @DeleteMapping(value = "/institutions/{institutionId}/roles/{roleId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable final UUID institutionId, @PathVariable final UUID roleId) {
    roleService.deleteAsPlatformAdmin(institutionId, roleId);
  }
}
