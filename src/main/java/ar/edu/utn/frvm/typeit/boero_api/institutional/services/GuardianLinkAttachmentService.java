package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentStorage;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentStorage.StoredFile;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardianAttachment;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAttachmentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianAttachmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents supporting a guardianship request. The tutor who owns the request uploads them while it
 * is pending; the institution reads them to decide. Reads take a {@code tutorPersonId} to scope the
 * caller to their own requests, or {@code null} for the institution's reviewers.
 */
@Service
@RequiredArgsConstructor
public class GuardianLinkAttachmentService {

  static final int MAX_ATTACHMENTS = 5;

  private final PersonGuardianRepository personGuardianRepository;
  private final PersonGuardianAttachmentRepository attachmentRepository;
  private final EnrollmentStorage storage;

  public record Content(Resource resource, PersonGuardianAttachment attachment) {}

  @Transactional
  public GuardianLinkAttachmentResponse upload(
      final UUID institutionId,
      final UUID tutorPersonId,
      final UUID linkId,
      final MultipartFile file) {
    final PersonGuardian link = lockPendingLink(institutionId, tutorPersonId, linkId);

    if (attachmentRepository.countByPersonGuardian(link) >= MAX_ATTACHMENTS) {
      throw new InvalidFileException(InstitutionMessages.GUARDIAN_LINK_ATTACHMENT_LIMIT);
    }

    final StoredFile stored = storage.store(linkId, file);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(final int status) {
            if (status == STATUS_ROLLED_BACK) {
              storage.deletePhysicalFile(stored.storagePath());
            }
          }
        });

    final String originalName =
        file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
            ? file.getOriginalFilename()
            : stored.safeFileName();

    return GuardianLinkAttachmentResponse.from(
        attachmentRepository.saveAndFlush(
            PersonGuardianAttachment.builder()
                .institution(link.getInstitution())
                .personGuardian(link)
                .originalFileName(originalName)
                .storagePath(stored.storagePath())
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .build()));
  }

  @Transactional
  public void delete(
      final UUID institutionId,
      final UUID tutorPersonId,
      final UUID linkId,
      final UUID attachmentId) {
    final PersonGuardian link = lockPendingLink(institutionId, tutorPersonId, linkId);
    final PersonGuardianAttachment attachment = findAttachment(link, attachmentId);

    attachmentRepository.delete(attachment);
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
  public Content content(
      final UUID institutionId,
      final @Nullable UUID tutorPersonId,
      final UUID linkId,
      final UUID attachmentId) {
    final PersonGuardian link = findVisibleLink(institutionId, tutorPersonId, linkId);
    final PersonGuardianAttachment attachment = findAttachment(link, attachmentId);

    return new Content(storage.loadAsResource(attachment.getStoragePath()), attachment);
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
