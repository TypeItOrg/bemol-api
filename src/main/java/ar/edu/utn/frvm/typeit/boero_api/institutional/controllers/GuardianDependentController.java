package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GuardianLinkAttachmentService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UnlinkGuardianDependentUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
  private final GuardianLinkAttachmentService guardianLinkAttachmentService;

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

  @PostMapping(
      value = "/{personGuardianId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  @ResponseStatus(HttpStatus.CREATED)
  public GuardianLinkAttachmentResponse uploadAttachment(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personGuardianId,
      @RequestParam("file") final MultipartFile file,
      final Authentication authentication) {
    return guardianLinkAttachmentService.upload(
        institutionId, currentPersonId(authentication), personGuardianId, file);
  }

  @GetMapping(value = "/{personGuardianId}/attachments", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  public List<GuardianLinkAttachmentResponse> listAttachments(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personGuardianId,
      final Authentication authentication) {
    return guardianLinkAttachmentService.list(
        institutionId, currentPersonId(authentication), personGuardianId);
  }

  @GetMapping(
      value = "/{personGuardianId}/attachments/{attachmentId}/content",
      version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  public ResponseEntity<Resource> downloadAttachment(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personGuardianId,
      @PathVariable final UUID attachmentId,
      final Authentication authentication) {
    return GuardianLinkAttachmentDownload.inline(
        guardianLinkAttachmentService.content(
            institutionId, currentPersonId(authentication), personGuardianId, attachmentId));
  }

  @DeleteMapping(value = "/{personGuardianId}/attachments/{attachmentId}", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_DEPENDENT_MANAGE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAttachment(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personGuardianId,
      @PathVariable final UUID attachmentId,
      final Authentication authentication) {
    guardianLinkAttachmentService.delete(
        institutionId, currentPersonId(authentication), personGuardianId, attachmentId);
  }

  private UUID currentPersonId(final Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);

    return ((JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal())).personId();
  }
}
