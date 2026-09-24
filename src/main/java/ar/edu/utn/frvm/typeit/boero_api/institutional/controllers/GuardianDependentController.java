package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UnlinkGuardianDependentUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/guardian/dependents")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class GuardianDependentController {

  private final InstitutionalCallerGuard institutionalCallerGuard;
  private final ListGuardianDependentsUseCase listGuardianDependentsUseCase;
  private final RegisterGuardianDependentUseCase registerGuardianDependentUseCase;
  private final UnlinkGuardianDependentUseCase unlinkGuardianDependentUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  public List<GuardianDependentResponse> list(
      @PathVariable final UUID institutionId, final Authentication authentication) {
    return listGuardianDependentsUseCase.execute(institutionId, currentPersonId(authentication));
  }

  @PostMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  @ResponseStatus(HttpStatus.CREATED)
  public GuardianDependentResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateGuardianDependentRequest request,
      final Authentication authentication) {
    return registerGuardianDependentUseCase.execute(
        institutionId, currentPersonId(authentication), request);
  }

  @DeleteMapping(value = "/{dependentPersonId}", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID dependentPersonId,
      final Authentication authentication) {
    unlinkGuardianDependentUseCase.execute(
        institutionId, currentPersonId(authentication), dependentPersonId);
  }

  private UUID currentPersonId(final Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);

    return ((JwtAuthenticatedUser) authentication.getPrincipal()).personId();
  }
}
