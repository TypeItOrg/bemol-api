package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentVersionStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentAttachmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentDocumentAudit.Actor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UploadEnrollmentAttachmentUseCase {
  private final EnrollmentDocumentAccess access;
  private final EnrollmentAttachmentRepository attachments;
  private final StorageService storage;
  private final EnrollmentDocumentAuthorization authorization;
  private final EnrollmentDocumentAudit audit;
  private final EnrollmentStorageJournal journal;
  private final EnrollmentDocumentRequirementsService documents;

  @Transactional
  public EnrollmentAttachmentResponse execute(
      final UUID applicationId,
      final MultipartFile file,
      final UUID requirementId,
      final @Nullable Authentication authentication) {
    final var application =
        access.lockApplication(
            applicationId,
            authentication,
            PermissionCode.ENROLLMENT_ATTACHMENT_UPLOAD,
            EnrollmentDocumentAction.UPLOAD,
            null);
    access.requireEditable(application);
    final var requirement = documents.requirement(application, requirementId);
    documents.requireActive(requirement);
    final var previous =
        attachments.findByEnrollmentApplicationIdAndRequirementIdAndVersionStatusAndDeletedAtIsNull(
            applicationId, requirementId, DocumentVersionStatus.CURRENT);
    if (previous.isPresent()) {
      previous.get().requireMutable();
      authorization.require(
          application,
          authentication,
          PermissionCode.ENROLLMENT_ATTACHMENT_DELETE,
          EnrollmentDocumentAction.REPLACE,
          previous.get().getId());
    }

    final var stored = EnrollmentFilePolicy.prepare(applicationId, file);
    if (!requirement.getAllowedFormats().contains(stored.contentType())) {
      throw new InvalidFileException(EnrollmentMessages.DOCUMENT_FORMAT_INVALID);
    }
    final var actor = Actor.from(authentication);
    final var reservation =
        journal.reserve(application.getInstitution().getId(), applicationId, stored, actor);

    // Keep the reservation locked until commit so cleanup cannot race the upload/confirmation.
    journal.lockReservation(reservation);
    storage.write(stored.storagePath(), stored.contentType(), stored.size(), file);
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
        file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            ? stored.safeFileName()
            : file.getOriginalFilename();
    final var attachment =
        EnrollmentAttachment.builder()
            .enrollmentApplication(application)
            .requirement(requirement)
            .uploadedBy(requireNonNull(actor.id()))
            .uploaderType(actor.accountType())
            .originalFileName(originalName)
            .storagePath(stored.storagePath())
            .contentType(stored.contentType())
            .fileSize(stored.size())
            .build();
    try {
      attachments.saveAndFlush(attachment);
      journal.confirm(reservation, attachment.getId());
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
