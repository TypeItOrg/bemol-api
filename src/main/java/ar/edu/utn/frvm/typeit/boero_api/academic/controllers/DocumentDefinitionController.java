package ar.edu.utn.frvm.typeit.boero_api.academic.controllers;

import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.*;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.DocumentCatalogUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.*;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.boero_api.common.web.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequiresInstitutionAccess
@RequestMapping("/institutions/{institutionId}/document-definitions")
public class DocumentDefinitionController {
  private final DocumentCatalogUseCase catalog;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<DocumentDefinitionResponse> list(
      @PathVariable UUID institutionId,
      @RequestParam(defaultValue = "") @Size(max = 150) String search,
      @RequestParam(required = false) @Nullable Boolean active,
      @RequestParam(required = false) @Nullable UUID trainingPathId,
      @RequestParam(required = false) @Nullable UUID applicationId,
      @RequestParam(defaultValue = "false") boolean forTrainingPathCreation,
      @PageableDefault(sort = {"name", "id"}) Pageable pageable) {
    return PaginatedResponse.from(
        catalog.list(
            institutionId,
            search,
            active,
            trainingPathId,
            applicationId,
            forTrainingPathCreation,
            pageable));
  }

  @GetMapping(value = "/{id}", version = Version.V1)
  @RequiresPermission(PermissionCode.DOCUMENT_CATALOG_READ)
  public DocumentDefinitionResponse get(@PathVariable UUID institutionId, @PathVariable UUID id) {
    return catalog.get(institutionId, id);
  }

  @PostMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.DOCUMENT_CATALOG_MANAGE)
  public DocumentCatalogSaveResponse create(
      @PathVariable UUID institutionId, @Valid @RequestBody DocumentDefinitionRequest request) {
    return catalog.save(institutionId, null, request);
  }

  @PutMapping(value = "/{id}", version = Version.V1)
  @RequiresPermission(PermissionCode.DOCUMENT_CATALOG_MANAGE)
  public DocumentCatalogSaveResponse update(
      @PathVariable UUID institutionId,
      @PathVariable UUID id,
      @Valid @RequestBody DocumentDefinitionRequest request) {
    return catalog.save(institutionId, id, request);
  }

  @GetMapping(value = "/{id}/training-paths", version = Version.V1)
  @RequiresPermission(PermissionCode.DOCUMENT_CATALOG_READ)
  public PaginatedResponse<DocumentRequirementResponse> associations(
      @PathVariable UUID institutionId,
      @PathVariable UUID id,
      @RequestParam(required = false) @Nullable UUID trainingPathId,
      @RequestParam(required = false) @Nullable Boolean active,
      @PageableDefault(sort = {"displayOrder", "id"}) Pageable pageable) {
    return PaginatedResponse.from(
        catalog.associations(institutionId, id, trainingPathId, active, pageable));
  }

  @PutMapping(value = "/{id}/training-paths", version = Version.V1)
  public DocumentCatalogSaveResponse assignments(
      @PathVariable UUID institutionId,
      @PathVariable UUID id,
      @Valid @RequestBody
          List<@jakarta.validation.constraints.NotNull @Valid DocumentAssignmentRequest> changes) {
    return catalog.saveAssignments(institutionId, id, changes);
  }
}
