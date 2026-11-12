package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.PresignedDownloadResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageFileNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFilePolicy;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFilePolicy.StoredFile;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardianAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkAttachmentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianAttachmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.ConfirmGuardianAttachmentUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.PresignGuardianAttachmentUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.PresignedGuardianAttachmentUploadResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Documents supporting a guardianship request. The tutor who owns the request uploads them while it
 * is pending; the institution reads them to decide. Reads take a {@code tutorPersonId} to scope the
 * caller to their own requests, or {@code null} for the institution's reviewers.
 */
@Service
@RequiredArgsConstructor
public class GuardianLinkAttachmentService {

  static final int MAX_ATTACHMENTS = 5;
  private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
  private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

  private final PersonGuardianRepository personGuardianRepository;
  private final PersonGuardianAttachmentRepository attachmentRepository;
  private final StorageService storage;
  private final AuditEventRecorder auditRecorder;

  @Transactional(readOnly = true)
  public PresignedGuardianAttachmentUploadResponse presignUpload(
      final UUID institutionId,
      final UUID tutorPersonId,
      final UUID linkId,
      final PresignGuardianAttachmentUploadRequest request) {
    final PersonGuardian link = findVisibleLink(institutionId, tutorPersonId, linkId);
    if (link.getStatus() != GuardianLinkStatus.PENDING) {
      throw new GuardianLinkAlreadyResolvedException();
    }
    if (attachmentRepository.countByPersonGuardian(link) >= MAX_ATTACHMENTS) {
      throw new InvalidFileException(InstitutionMessages.GUARDIAN_LINK_ATTACHMENT_LIMIT);
    }

    final StoredFile stored =
        EnrollmentFilePolicy.prepare(
            linkId, request.fileName(), request.contentType(), request.fileSize());
    final var presigned =
        storage.presignUpload(
            stored.storagePath(), stored.contentType(), stored.size(), UPLOAD_TTL);

    return PresignedGuardianAttachmentUploadResponse.from(stored.storagePath(), presigned);
  }

  @Transactional
  public GuardianLinkAttachmentResponse confirmUpload(
      final UUID institutionId,
      final UUID tutorPersonId,
      final UUID linkId,
      final ConfirmGuardianAttachmentUploadRequest request) {
    final PersonGuardian link = lockPendingLink(institutionId, tutorPersonId, linkId);

    if (attachmentRepository.countByPersonGuardian(link) >= MAX_ATTACHMENTS) {
      throw new InvalidFileException(InstitutionMessages.GUARDIAN_LINK_ATTACHMENT_LIMIT);
    }

    final StoredFile stored =
        EnrollmentFilePolicy.prepare(
            linkId, request.fileName(), request.contentType(), request.fileSize());
    validateLinkStorageKey(linkId, request.storageKey(), stored.contentType());

    try {
      final var actual = storage.headObject(request.storageKey());
      EnrollmentFilePolicy.verifyUploadedObject(actual, stored.contentType(), stored.size());
    } catch (StorageFileNotFoundException exception) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }

    final String originalName =
        EnrollmentFilePolicy.resolveOriginalFileName(request.fileName(), stored.safeFileName());

    final var attachment =
        attachmentRepository.saveAndFlush(
            PersonGuardianAttachment.builder()
                .institution(link.getInstitution())
                .personGuardian(link)
                .originalFileName(originalName)
                .storagePath(request.storageKey())
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .build());
    final var details =
        AuditChangeDetails.change(
            "Adjunto de vinculación", AuditSnapshot.empty(), attachment.auditSnapshot());
    details.put("relatedPersonIds", List.of(link.getTutorPerson().getId().toString()));
    auditRecorder.recordCurrentActor(
        link.getInstitution(),
        link.getDependentPersonId(),
        InstitutionalAuditAction.GUARDIAN_ATTACHMENT_UPLOADED,
        PersonGuardianAttachment.AUDIT_TYPE,
        attachment.getId(),
        AuditOrigin.SELF_SERVICE,
        details);
    return GuardianLinkAttachmentResponse.from(attachment);
  }

  @Transactional
  public void delete(
      final UUID institutionId,
      final UUID tutorPersonId,
      final UUID linkId,
      final UUID attachmentId) {
    final PersonGuardian link = lockPendingLink(institutionId, tutorPersonId, linkId);
    final PersonGuardianAttachment attachment = findAttachment(link, attachmentId);

    final var details =
        AuditChangeDetails.change(
            "Adjunto de vinculación", attachment.auditSnapshot(), AuditSnapshot.empty());
    details.put("relatedPersonIds", List.of(link.getTutorPerson().getId().toString()));
    attachmentRepository.delete(attachment);
    auditRecorder.recordCurrentActor(
        link.getInstitution(),
        link.getDependentPersonId(),
        InstitutionalAuditAction.GUARDIAN_ATTACHMENT_DELETED,
        PersonGuardianAttachment.AUDIT_TYPE,
        attachmentId,
        AuditOrigin.SELF_SERVICE,
        details);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            storage.deletePhysicalFile(attachment.getStoragePath());
          }
        });
  }

  @Transactional(readOnly = true)
  public List<GuardianLinkAttachmentResponse> list(
      final UUID institutionId, final @Nullable UUID tutorPersonId, final UUID linkId) {
    final PersonGuardian link = findVisibleLink(institutionId, tutorPersonId, linkId);

    return attachmentRepository.findByPersonGuardianOrderByCreatedAtAsc(link).stream()
        .map(GuardianLinkAttachmentResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PresignedDownloadResponse content(
      final UUID institutionId,
      final @Nullable UUID tutorPersonId,
      final UUID linkId,
      final UUID attachmentId) {
    final PersonGuardian link = findVisibleLink(institutionId, tutorPersonId, linkId);
    final PersonGuardianAttachment attachment = findAttachment(link, attachmentId);

    return PresignedDownloadResponse.from(
        storage.presignDownload(
            attachment.getStoragePath(),
            attachment.getContentType(),
            attachment.getOriginalFileName(),
            DOWNLOAD_TTL));
  }

  private static void validateLinkStorageKey(
      final UUID linkId, final String storageKey, final String contentType) {
    final String prefix = "enrollments/" + linkId + "/";
    final String extension = EnrollmentFilePolicy.extensionFor(contentType);
    if (!storageKey.startsWith(prefix) || !storageKey.endsWith(extension)) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
    final String uuidPart =
        storageKey.substring(prefix.length(), storageKey.length() - extension.length());
    try {
      UUID.fromString(uuidPart);
    } catch (IllegalArgumentException exception) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
  }

  /**
   * Locking serializes uploads with the institution's decision, so nothing is attached to a request
   * that is being resolved.
   */
  private PersonGuardian lockPendingLink(
      final UUID institutionId, final UUID tutorPersonId, final UUID linkId) {
    final PersonGuardian link =
        ownedBy(
            tutorPersonId,
            personGuardianRepository
                .findForUpdate(linkId, institutionId)
                .orElseThrow(GuardianLinkNotFoundException::new));

    if (link.getStatus() != GuardianLinkStatus.PENDING) {
      throw new GuardianLinkAlreadyResolvedException();
    }

    return link;
  }

  private PersonGuardian findVisibleLink(
      final UUID institutionId, final @Nullable UUID tutorPersonId, final UUID linkId) {
    return ownedBy(
        tutorPersonId,
        personGuardianRepository
            .findByIdAndInstitution_Id(linkId, institutionId)
            .orElseThrow(GuardianLinkNotFoundException::new));
  }

  /** A link of someone else is reported as missing, so its existence is not revealed. */
  private PersonGuardian ownedBy(final @Nullable UUID tutorPersonId, final PersonGuardian link) {
    if (tutorPersonId != null && !link.getTutorPerson().getId().equals(tutorPersonId)) {
      throw new GuardianLinkNotFoundException();
    }

    return link;
  }

  private PersonGuardianAttachment findAttachment(
      final PersonGuardian link, final UUID attachmentId) {
    return attachmentRepository
        .findByIdAndPersonGuardian(attachmentId, link)
        .orElseThrow(GuardianLinkAttachmentNotFoundException::new);
  }
}
