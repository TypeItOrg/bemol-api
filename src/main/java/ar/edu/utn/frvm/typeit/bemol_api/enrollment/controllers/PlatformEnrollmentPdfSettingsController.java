package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfSettingsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ExtractEnrollmentPdfDataUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/institutions/{institutionId}/enrollment-pdf-settings")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class PlatformEnrollmentPdfSettingsController {
  private final EnrollmentPdfSettingsUseCase settings;
  private final ExtractEnrollmentPdfDataUseCase pdf;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public EnrollmentPdfSettingsResponse get(@PathVariable final UUID institutionId) {
    return settings.get(institutionId);
  }

  @PutMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentPdfSettingsResponse replace(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final EnrollmentPdfSettingsRequest input) {
    return settings.replace(institutionId, input);
  }

  @PostMapping(value = "/preview", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentPdfData preview(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final EnrollmentPdfSettingsRequest input) {
    return pdf.example(institutionId, input);
  }
}
