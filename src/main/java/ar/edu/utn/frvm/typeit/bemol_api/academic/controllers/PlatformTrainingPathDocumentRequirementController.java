package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.DocumentRequirementRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.DocumentRequirementResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.TrainingPathDocumentRequirementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequiredArgsConstructor
@RequestMapping("/admin/institutions/{institutionId}/training-paths/{pathId}/document-requirements")
public class PlatformTrainingPathDocumentRequirementController {
  private final TrainingPathDocumentRequirementService service;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.TRAINING_PATH_READ)
  public List<DocumentRequirementResponse> list(
      @PathVariable UUID institutionId, @PathVariable UUID pathId) {
    return service.list(institutionId, pathId);
  }

  @PostMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.TRAINING_PATH_UPDATE)
  public DocumentRequirementResponse create(
      @PathVariable UUID institutionId,
      @PathVariable UUID pathId,
      @Valid @RequestBody DocumentRequirementRequest request) {
    return service.save(institutionId, pathId, null, request);
  }

  @PutMapping(value = "/{id}", version = Version.V1)
  @RequiresPermission(PermissionCode.TRAINING_PATH_UPDATE)
  public DocumentRequirementResponse update(
      @PathVariable UUID institutionId,
      @PathVariable UUID pathId,
      @PathVariable UUID id,
      @Valid @RequestBody DocumentRequirementRequest request) {
    return service.save(institutionId, pathId, id, request);
  }
}
