package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormDefinitionUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRenameRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormCatalogUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormVersionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ExtractEnrollmentPdfDataUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/institutions/{institutionId}/enrollment-forms")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class PlatformEnrollmentFormController {
  private final EnrollmentFormCatalogUseCase catalog;
  private final EnrollmentFormVersionUseCase versions;
  private final ExtractEnrollmentPdfDataUseCase pdf;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public PaginatedResponse<EnrollmentFormResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 150) final @Nullable String search,
      @PageableDefault(sort = {"name", "id"}) final Pageable pageable) {
    return PaginatedResponse.from(catalog.list(institutionId, search, pageable));
  }

  @GetMapping(value = "/base", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public EnrollmentFormDefinition base(@PathVariable final UUID institutionId) {
    return catalog.base(institutionId);
  }

  @PostMapping(value = "/preview", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormDefinition preview(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final EnrollmentFormDefinition definition) {
    return catalog.preview(institutionId, definition);
  }

  @GetMapping(value = "/{formId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public EnrollmentFormResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID formId) {
    return catalog.get(institutionId, formId);
  }

  @PostMapping(value = "/preview-pdf", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentPdfData previewPdf(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final EnrollmentFormDefinition definition) {
    return pdf.formExample(institutionId, definition);
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final EnrollmentFormCreateRequest request) {
    return catalog.create(institutionId, request);
  }

  @PutMapping(value = "/{formId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormResponse rename(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @Valid @RequestBody final EnrollmentFormRenameRequest request) {
    return catalog.rename(institutionId, formId, request);
  }

  @GetMapping(value = "/{formId}/versions", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public PaginatedResponse<EnrollmentFormVersionSummaryResponse> versions(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @RequestParam(required = false) @Size(max = 150) final @Nullable String search,
      @PageableDefault(
              sort = {"versionNumber", "id"},
              direction = Sort.Direction.DESC)
          final Pageable pageable) {
    return PaginatedResponse.from(versions.list(institutionId, formId, search, pageable));
  }

  @GetMapping(value = "/{formId}/versions/{versionId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public EnrollmentFormVersionResponse version(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @PathVariable final UUID versionId) {
    return versions.get(institutionId, formId, versionId);
  }

  @PostMapping(value = "/{formId}/versions", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormVersionResponse createVersion(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @Valid @RequestBody final EnrollmentFormVersionCreateRequest request) {
    return versions.createDraft(institutionId, formId, request);
  }

  @PutMapping(value = "/{formId}/versions/{versionId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormVersionResponse replaceVersion(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @PathVariable final UUID versionId,
      @Valid @RequestBody final EnrollmentFormDefinitionUpdateRequest request) {
    return versions.replace(institutionId, formId, versionId, request);
  }

  @PostMapping(value = "/{formId}/versions/{versionId}/publish", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormVersionResponse publish(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @PathVariable final UUID versionId,
      @Valid @RequestBody final EnrollmentFormRevisionRequest request) {
    return versions.publish(institutionId, formId, versionId, request);
  }

  @PostMapping(value = "/{formId}/versions/{versionId}/archive", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormVersionResponse archive(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID formId,
      @PathVariable final UUID versionId,
      @Valid @RequestBody final EnrollmentFormRevisionRequest request) {
    return versions.archive(institutionId, formId, versionId, request);
  }
}
