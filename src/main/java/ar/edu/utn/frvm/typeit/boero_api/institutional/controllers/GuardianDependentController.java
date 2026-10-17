package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.OwnAccessResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.UpdateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GuardianLinkAttachmentService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UnlinkGuardianDependentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/institutions/{institutionId}/representations")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class GuardianDependentController {

  private final PersonOwnAccessService ownAccess;
  private final InstitutionalCallerGuard institutionalCallerGuard;
  private final ListGuardianDependentsUseCase listGuardianDependentsUseCase;
  private final RegisterGuardianDependentUseCase registerGuardianDependentUseCase;
  private final UnlinkGuardianDependentUseCase unlinkGuardianDependentUseCase;
  private final GuardianLinkAttachmentService guardianLinkAttachmentService;

  @PatchMapping(value = "/{personGuardianId}/own-access", version = Version.V1)
  public OwnAccessResponse updateOwnAccess(
      @PathVariable UUID institutionId,
      @PathVariable UUID personGuardianId,
      @Valid @RequestBody UpdateOwnAccessRequest request,
      Authentication authentication) {
    return ownAccess.update(
        institutionId, currentPersonId(authentication), personGuardianId, request);
  }

  @GetMapping(version = Version.V1)
  public List<GuardianDependentResponse> list(
      @PathVariable final UUID institutionId, final Authentication authentication) {
    return listGuardianDependentsUseCase.execute(institutionId, currentPersonId(authentication));
  }

  @GetMapping(value = "/page", version = Version.V1)
  public PaginatedResponse<GuardianDependentResponse> page(
      @PathVariable final UUID institutionId,
      @RequestParam(defaultValue = "0") @Min(0) final int page,
      @RequestParam(defaultValue = "10") @Pattern(regexp = "10|20|30|40|50") final String size,
      @RequestParam(defaultValue = "") @Size(max = 100) final String search,
      @RequestParam(required = false) final @Nullable GuardianLinkStatus status,
      @RequestParam(required = false) final @Nullable GuardianRelationship relationship,
      @RequestParam(required = false) final @Nullable Boolean primaryContact,
      @RequestParam(defaultValue = "createdAt")
          @Pattern(regexp = "firstName|lastName|documentNumber|status|relationship|createdAt")
          final String sortField,
      @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") final String sortDirection,
      final Authentication authentication) {
    return listGuardianDependentsUseCase.page(
        institutionId,
        currentPersonId(authentication),
        page,
        Integer.parseInt(size),
        search,
        status,
        relationship,
        primaryContact,
        sortField,
        sortDirection);
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public GuardianDependentResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateGuardianDependentRequest request,
      final Authentication authentication) {
    return registerGuardianDependentUseCase.execute(
        institutionId, currentPersonId(authentication), request);
  }

  @DeleteMapping(value = "/{dependentPersonId}", version = Version.V1)
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
