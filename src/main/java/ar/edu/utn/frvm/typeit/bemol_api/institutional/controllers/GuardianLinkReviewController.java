package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.ApproveGuardianLinkRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GuardianLinkAttachmentService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListGuardianLinksUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ResolveGuardianLinkUseCase;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @GetMapping(value = "/page", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public PaginatedResponse<GuardianLinkReviewResponse> page(
      @PathVariable final UUID institutionId,
      @RequestParam(defaultValue = "0") @Min(0) final int page,
      @RequestParam(defaultValue = "10") @Pattern(regexp = "10|20|30|40|50") final String size,
      @RequestParam(defaultValue = "") @Size(max = 100) final String search,
      @RequestParam(required = false) final @Nullable GuardianLinkStatus status,
      @RequestParam(required = false) final @Nullable Boolean hasDocumentation,
      @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "createdAt|status")
          final String sortField,
      @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc")
          final String sortDirection) {
    return listGuardianLinksUseCase.page(
        institutionId,
        page,
        Integer.parseInt(size),
        search,
        status,
        hasDocumentation,
        sortField,
        sortDirection);
  }

  @PostMapping(value = "/{linkId}/approve", version = Version.V1)
  @RequiresPermission(PermissionCode.GUARDIAN_LINK_REVIEW)
  public GuardianLinkReviewResponse approve(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID linkId,
      @RequestBody(required = false) final ApproveGuardianLinkRequest request,
      final Authentication authentication) {
    return resolveGuardianLinkUseCase.approve(
        institutionId,
        currentPersonId(authentication),
        linkId,
        request == null ? null : request.confirmedPersonId(),
        ((JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal())).actorPersonId());
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
