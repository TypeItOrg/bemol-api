package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/guardian-links")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequiredArgsConstructor
public class PlatformGuardianLinkController {

  private final ListGuardianLinksUseCase listGuardianLinksUseCase;
  private final ResolveGuardianLinkUseCase resolveGuardianLinkUseCase;
  private final GuardianLinkAttachmentService guardianLinkAttachmentService;

  @GetMapping(version = Version.V1)
  public List<GuardianLinkReviewResponse> list(
      @RequestParam(defaultValue = "PENDING") final GuardianLinkStatus status) {
    return listGuardianLinksUseCase.executeForPlatform(status);
  }

  @PostMapping(value = "/{institutionId}/{linkId}/approve", version = Version.V1)
  public GuardianLinkReviewResponse approve(
      @PathVariable final UUID institutionId, @PathVariable final UUID linkId) {
    return resolveGuardianLinkUseCase.approveForPlatform(institutionId, linkId);
  }

  @PostMapping(value = "/{institutionId}/{linkId}/reject", version = Version.V1)
  public GuardianLinkReviewResponse reject(
      @PathVariable final UUID institutionId, @PathVariable final UUID linkId) {
    return resolveGuardianLinkUseCase.rejectForPlatform(institutionId, linkId);
  }

  @GetMapping(value = "/{institutionId}/{linkId}/attachments", version = Version.V1)
  public List<GuardianLinkAttachmentResponse> listAttachments(
      @PathVariable final UUID institutionId, @PathVariable final UUID linkId) {
    return guardianLinkAttachmentService.list(institutionId, null, linkId);
  }

  @GetMapping(
      value = "/{institutionId}/{linkId}/attachments/{attachmentId}/content",
      version = Version.V1)
  public ResponseEntity<Resource> downloadAttachment(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID linkId,
      @PathVariable final UUID attachmentId) {
    return GuardianLinkAttachmentDownload.inline(
        guardianLinkAttachmentService.content(institutionId, null, linkId, attachmentId));
  }
}
