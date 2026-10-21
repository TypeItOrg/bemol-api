package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.TrainingPathScopeOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ListAssignableTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoleTrainingPathOptionsController {
  private final ListAssignableTrainingPathsUseCase useCase;

  @GetMapping(
      value = "/institutions/{institutionId}/roles/training-path-options",
      version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresAnyPermission({
    PermissionCode.INSTITUTION_ROLE_ASSIGN,
    PermissionCode.INSTITUTION_ROLE_REVOKE
  })
  public PaginatedResponse<TrainingPathScopeOptionResponse> institutional(
      @PathVariable UUID institutionId,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return useCase.execute(institutionId, search, pageable);
  }

  @GetMapping(
      value = "/admin/institutions/{institutionId}/roles/training-path-options",
      version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PaginatedResponse<TrainingPathScopeOptionResponse> platform(
      @PathVariable UUID institutionId,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return useCase.execute(institutionId, search, pageable);
  }
}
