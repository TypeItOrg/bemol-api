package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.UpdateInstitutionalInstitutionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionalInstitutionUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/institutions/{institutionId}")
@RequiredArgsConstructor
@RequiresInstitutionAccess
public class InstitutionalInstitutionController {

  private final UpdateInstitutionalInstitutionUseCase updateInstitutionalInstitutionUseCase;
  private final InstitutionLogoUseCase logos;

  @PutMapping(value = "/logo", version = Version.V1, consumes = "multipart/form-data")
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public InstitutionDetailResponse replaceLogo(
      @PathVariable final UUID institutionId,
      @RequestPart("file") final MultipartFile file,
      final Authentication authentication) {
    return logos.replace(institutionId, file);
  }

  @DeleteMapping(value = "/logo", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteLogo(
      @PathVariable final UUID institutionId, final Authentication authentication) {
    logos.delete(institutionId);
  }

  @PutMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public InstitutionDetailResponse update(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final UpdateInstitutionalInstitutionRequest request,
      final Authentication authentication) {
    return updateInstitutionalInstitutionUseCase.execute(institutionId, request);
  }
}
