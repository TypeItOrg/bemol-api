package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementOrigin;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.AttachmentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentAttachmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.DocumentReviewRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentDocumentRequirementResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentDocumentAudit.Actor;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class EnrollmentAttachmentService {
  private final EnrollmentAttachmentRepository attachments;
  private final StorageService storage;
  private final EnrollmentDocumentAuthorization authorization;
  private final EnrollmentDocumentAudit audit;
  private final Clock clock;
  private final EnrollmentDocumentRequirementsService documents;
  private final EnrollmentDocumentAccess access;
  private final UploadEnrollmentAttachmentUseCase upload;

  public record AttachmentContentResult(Resource resource, EnrollmentAttachment attachment) {}

  @Transactional
  public EnrollmentAttachmentResponse uploadAttachment(
      final UUID applicationId,
      final MultipartFile file,
      final UUID requirementId,
      final @Nullable Authentication authentication) {
    return upload.execute(applicationId, file, requirementId, authentication);
  }

  @Transactional(readOnly = true)
  public AttachmentContentResult getAttachmentContent(
      final UUID applicationId,
      final UUID attachmentId,
      final @Nullable Authentication authentication) {
    final var application = access.activeApplication(applicationId);
    authorization.require(
        application,
        authentication,
        PermissionCode.ENROLLMENT_ATTACHMENT_READ,
        EnrollmentDocumentAction.CONTENT_ACCESS,
        attachmentId);
    final var attachment = attachment(applicationId, attachmentId);

    // Commit the access event before opening the stream, even in a read-only transaction.
    audit.recordIndependent(
        authentication,
        application.getInstitution().getId(),
        applicationId,
        attachmentId,
        EnrollmentDocumentAction.CONTENT_ACCESS,
        "ALLOWED");

    return new AttachmentContentResult(
        storage.loadAsResource(attachment.getStoragePath()), attachment);
  }

  @Transactional
  public void deleteAttachment(
      final UUID applicationId,
      final UUID attachmentId,
      final @Nullable Authentication authentication) {
    final var application =
        access.lockApplication(
            applicationId,
            authentication,
            PermissionCode.ENROLLMENT_ATTACHMENT_DELETE,
            EnrollmentDocumentAction.DELETE,
            attachmentId);
    access.requireEditable(application);
    final var attachment = attachment(applicationId, attachmentId);
    final var actor = Actor.from(authentication);

    documents.requireActive(attachment.getRequirement());
    attachment.requireMutable();
    if (!application.isEditable()
        && attachment.getRequirement().getOrigin() == DocumentRequirementOrigin.ORIGINAL
        && attachment.getRequirement().getLevel() == DocumentRequirementLevel.AT_SUBMISSION) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_WITHDRAW_DENIED);
    }

    attachment.withdraw();
    attachments.save(attachment);
    audit.record(
        actor,
        application.getInstitution().getId(),
        applicationId,
        attachmentId,
        EnrollmentDocumentAction.DELETE,
        "SUCCESS");
  }

  @Transactional(readOnly = true)
  public List<EnrollmentAttachmentResponse> listAttachments(
      final UUID applicationId, final @Nullable Authentication authentication) {
    final var application = access.activeApplication(applicationId);
    requireMetadataAccess(application, authentication);
    recordMetadataAccess(application, authentication);

    return attachments.findByEnrollmentApplicationIdAndDeletedAtIsNull(applicationId).stream()
        .filter(attachment -> attachment.isCurrent())
        .map(EnrollmentAttachmentResponse::from)
        .toList();
  }

  @Transactional
  public EnrollmentAttachmentResponse review(
      final UUID applicationId,
      final UUID attachmentId,
      final DocumentReviewRequest request,
      final Authentication authentication) {
    final var application =
        access.lockApplication(
            applicationId,
            authentication,
            PermissionCode.ENROLLMENT_ATTACHMENT_REVIEW,
            EnrollmentDocumentAction.REVIEW,
            attachmentId);
    access.requireEditable(application);
    if (application.isEditable()) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_REVIEW_INVALID);
    }
    final var attachment = attachment(applicationId, attachmentId);
    final var actor = Actor.from(authentication);
    documents.requireActive(attachment.getRequirement());

    attachment.review(
        request.status(),
        request.observation(),
        requireNonNull(actor.id()),
        actor.accountType(),
        clock.instant());
    audit.record(
        actor,
        application.getInstitution().getId(),
        applicationId,
        attachmentId,
        EnrollmentDocumentAction.REVIEW,
        "SUCCESS");

    return EnrollmentAttachmentResponse.from(attachment);
  }

  @Transactional(readOnly = true)
  public List<EnrollmentDocumentRequirementResponse> requirements(
      final UUID applicationId, final Authentication authentication) {
    final var application = access.activeApplication(applicationId);
    requireMetadataAccess(application, authentication);
    recordMetadataAccess(application, authentication);

    return documents.responses(application, authentication);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<EnrollmentAttachmentResponse> history(
      final UUID applicationId,
      final UUID requirementId,
      final Pageable pageable,
      final Authentication authentication) {
    final var application = access.activeApplication(applicationId);
    requireMetadataAccess(application, authentication);
    documents.requirement(application, requirementId);
    recordMetadataAccess(application, authentication);

    return PaginatedResponse.from(
        attachments
            .findByEnrollmentApplicationIdAndRequirementIdAndDeletedAtIsNull(
                applicationId, requirementId, pageable)
            .map(EnrollmentAttachmentResponse::from));
  }

  private EnrollmentAttachment attachment(final UUID applicationId, final UUID attachmentId) {
    return attachments
        .findByIdAndEnrollmentApplicationIdAndDeletedAtIsNull(attachmentId, applicationId)
        .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
  }

  private void requireMetadataAccess(
      final EnrollmentApplication application, final @Nullable Authentication authentication) {
    authorization.require(
        application,
        authentication,
        PermissionCode.ENROLLMENT_ATTACHMENT_READ,
        EnrollmentDocumentAction.METADATA_READ,
        null);
  }

  private void recordMetadataAccess(
      final EnrollmentApplication application, final @Nullable Authentication authentication) {
    audit.recordIndependent(
        authentication,
        application.getInstitution().getId(),
        application.getId(),
        null,
        EnrollmentDocumentAction.METADATA_READ,
        "ALLOWED");
  }
}
