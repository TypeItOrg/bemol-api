package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreatePrerequisiteRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.PrerequisiteResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdatePrerequisiteRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreatePrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.DeletePrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetPrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListPrerequisitesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdatePrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/institutions/{institutionId}")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequiredArgsConstructor
public class AdminPrerequisiteController {

  private final CreatePrerequisiteUseCase createPrerequisiteUseCase;
  private final ListPrerequisitesUseCase listPrerequisitesUseCase;
  private final GetPrerequisiteUseCase getPrerequisiteUseCase;
  private final UpdatePrerequisiteUseCase updatePrerequisiteUseCase;
  private final DeletePrerequisiteUseCase deletePrerequisiteUseCase;

  @PostMapping(value = "/study-plan-spaces/{targetId}/prerequisites", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public PrerequisiteResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID targetId,
      @Valid @RequestBody final CreatePrerequisiteRequest request) {
    return createPrerequisiteUseCase.execute(institutionId, targetId, request);
  }

  @GetMapping(value = "/study-plan-spaces/{targetId}/prerequisites", version = Version.V1)
  public List<PrerequisiteResponse> list(
      @PathVariable final UUID institutionId, @PathVariable final UUID targetId) {
    return listPrerequisitesUseCase.execute(institutionId, targetId);
  }

  @GetMapping(value = "/prerequisites/{prerequisiteId}", version = Version.V1)
  public PrerequisiteResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID prerequisiteId) {
    return getPrerequisiteUseCase.execute(institutionId, prerequisiteId);
  }

  @PutMapping(value = "/prerequisites/{prerequisiteId}", version = Version.V1)
  public PrerequisiteResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID prerequisiteId,
      @Valid @RequestBody final UpdatePrerequisiteRequest request) {
    return updatePrerequisiteUseCase.execute(institutionId, prerequisiteId, request);
  }

  @DeleteMapping(value = "/prerequisites/{prerequisiteId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable final UUID institutionId, @PathVariable final UUID prerequisiteId) {
    deletePrerequisiteUseCase.execute(institutionId, prerequisiteId);
  }
}
