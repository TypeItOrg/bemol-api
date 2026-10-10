package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GuardianLinkAttachmentService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianLinksUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ResolveGuardianLinkUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Institutional review of the guardianship requests that tutors submit. */
@RestController
@RequestMapping("/institutions/{institutionId}/guardian-links")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class GuardianLinkReviewController {

  private final InstitutionalCallerGuard institutionalCallerGuard;
  private final ListGuardianLinksUseCase listGuardianLinksUseCase;
  private final ResolveGuardianLinkUseCase resolveGuardianLinkUseCase;
  private final GuardianLinkAttachmentService guardianLinkAttachmentService;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public List<GuardianLinkReviewResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(defaultValue = "PENDING") final GuardianLinkStatus status) {
    return listGuardianLinksUseCase.execute(institutionId, status);
  }

  @PostMapping(value = "/{linkId}/approve", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public GuardianLinkReviewResponse approve(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID linkId,
      final Authentication authentication) {
    return resolveGuardianLinkUseCase.approve(
        institutionId, currentPersonId(authentication), linkId);
  }

  @PostMapping(value = "/{linkId}/reject", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public GuardianLinkReviewResponse reject(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID linkId,
      final Authentication authentication) {
    return resolveGuardianLinkUseCase.reject(
        institutionId, currentPersonId(authentication), linkId);
  }

  @GetMapping(value = "/{linkId}/attachments", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public List<GuardianLinkAttachmentResponse> listAttachments(
      @PathVariable final UUID institutionId, @PathVariable final UUID linkId) {
    return guardianLinkAttachmentService.list(institutionId, null, linkId);
  }

  @GetMapping(value = "/{linkId}/attachments/{attachmentId}/content", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public ResponseEntity<Resource> downloadAttachment(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID linkId,
      @PathVariable final UUID attachmentId) {
    return GuardianLinkAttachmentDownload.inline(
        guardianLinkAttachmentService.content(institutionId, null, linkId, attachmentId));
  }

  private UUID currentPersonId(final Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);

    return ((JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal())).personId();
  }
}
