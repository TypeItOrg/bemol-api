package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageFileNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentAttachmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.ConfirmEnrollmentAttachmentUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PresignEnrollmentAttachmentUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PresignedAttachmentUploadResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentDocumentAudit.Actor;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UploadEnrollmentAttachmentUseCase {
  private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);

  private final EnrollmentDocumentAccess access;
  private final EnrollmentAttachmentRepository attachments;
  private final StorageService storage;
  private final EnrollmentDocumentAuthorization authorization;
  private final EnrollmentDocumentAudit audit;
  private final EnrollmentStorageJournal journal;
  private final EnrollmentDocumentRequirementsService documents;

  @Transactional
  public PresignedAttachmentUploadResponse presignUpload(
      final UUID applicationId,
      final PresignEnrollmentAttachmentUploadRequest request,
      final @Nullable Authentication authentication) {
    final var application =
        access.lockApplication(
            applicationId,
            authentication,
            PermissionCode.ENROLLMENT_ATTACHMENT_UPLOAD,
            EnrollmentDocumentAction.UPLOAD,
            null);
    access.requireEditable(application);
    final var requirement = documents.requirement(application, request.requirementId());
    documents.requireActive(requirement);
    final var previous =
        attachments.findByEnrollmentApplicationIdAndRequirementIdAndVersionStatusAndDeletedAtIsNull(
            applicationId, request.requirementId(), DocumentVersionStatus.CURRENT);
    if (previous.isPresent()) {
      previous.get().requireMutable();
      authorization.require(
          application,
          authentication,
          PermissionCode.ENROLLMENT_ATTACHMENT_DELETE,
          EnrollmentDocumentAction.REPLACE,
          previous.get().getId());
    }

    final var stored =
        EnrollmentFilePolicy.prepare(
            applicationId, request.fileName(), request.contentType(), request.fileSize());
    if (!requirement.getAllowedFormats().contains(stored.contentType())) {
      throw new InvalidFileException(EnrollmentMessages.DOCUMENT_FORMAT_INVALID);
    }
    final var actor = Actor.from(authentication);
    final var reservation =
        journal.reserve(application.getInstitution().getId(), applicationId, stored, actor);
    final var presigned =
        storage.presignUpload(
            stored.storagePath(), stored.contentType(), stored.size(), UPLOAD_TTL);

    return PresignedAttachmentUploadResponse.from(reservation, presigned);
  }

  @Transactional
  public EnrollmentAttachmentResponse confirmUpload(
      final UUID applicationId,
      final ConfirmEnrollmentAttachmentUploadRequest request,
      final @Nullable Authentication authentication) {
    final var application =
        access.lockApplication(
            applicationId,
            authentication,
            PermissionCode.ENROLLMENT_ATTACHMENT_UPLOAD,
            EnrollmentDocumentAction.UPLOAD,
            null);
    access.requireEditable(application);
    final var requirement = documents.requirement(application, request.requirementId());
    documents.requireActive(requirement);
    final var previous =
        attachments.findByEnrollmentApplicationIdAndRequirementIdAndVersionStatusAndDeletedAtIsNull(
            applicationId, request.requirementId(), DocumentVersionStatus.CURRENT);
    if (previous.isPresent()) {
      previous.get().requireMutable();
      authorization.require(
          application,
          authentication,
          PermissionCode.ENROLLMENT_ATTACHMENT_DELETE,
          EnrollmentDocumentAction.REPLACE,
          previous.get().getId());
    }

    final var stored =
        EnrollmentFilePolicy.prepare(
            applicationId, request.fileName(), request.contentType(), request.fileSize());
    if (!requirement.getAllowedFormats().contains(stored.contentType())) {
      throw new InvalidFileException(EnrollmentMessages.DOCUMENT_FORMAT_INVALID);
    }

    // Keep the reservation locked until commit so cleanup cannot race confirmation.
    final var job = journal.lockReservation(request.uploadId(), applicationId);
    if (!job.storagePath().endsWith(EnrollmentFilePolicy.extensionFor(stored.contentType()))) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
    try {
      final var actual = storage.headObject(job.storagePath());
      EnrollmentFilePolicy.verifyUploadedObject(actual, stored.contentType(), stored.size());
    } catch (StorageFileNotFoundException exception) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }

    final var actor = Actor.from(authentication);
    if (previous.isPresent()) {
      final var existing = previous.get();
      existing.supersede();
      attachments.saveAndFlush(existing);
      audit.record(
          actor,
          application.getInstitution().getId(),
          applicationId,
          existing.getId(),
          EnrollmentDocumentAction.DELETE,
          "SUCCESS");
    }

    final String originalName =
        EnrollmentFilePolicy.resolveOriginalFileName(request.fileName(), stored.safeFileName());
    final var attachment =
        EnrollmentAttachment.builder()
            .enrollmentApplication(application)
            .requirement(requirement)
            .uploadedBy(requireNonNull(actor.id()))
            .uploaderType(actor.accountType())
            .originalFileName(originalName)
            .storagePath(job.storagePath())
            .contentType(stored.contentType())
            .fileSize(stored.size())
            .build();
    try {
      attachments.saveAndFlush(attachment);
      journal.confirm(job.id(), attachment.getId());
      audit.record(
          actor,
          application.getInstitution().getId(),
          applicationId,
          attachment.getId(),
          previous.isPresent() ? EnrollmentDocumentAction.REPLACE : EnrollmentDocumentAction.UPLOAD,
          "SUCCESS");
    } catch (DataIntegrityViolationException exception) {
      throw EnrollmentIntegrityViolationTranslator.attachment(exception);
    }

    return EnrollmentAttachmentResponse.from(attachment);
  }
}
