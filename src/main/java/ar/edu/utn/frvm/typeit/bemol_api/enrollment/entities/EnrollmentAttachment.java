package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentReviewStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@AuditOverride(forClass = SoftDeletable.class, name = "deletedAt", isAudited = true)
@Table(name = "enrollment_attachments")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnrollmentAttachment extends SoftDeletable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "enrollment_attachment_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_application_id", nullable = false)
  @Setter
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentApplication enrollmentApplication;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requirement_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentDocumentRequirement requirement;

  @Enumerated(EnumType.STRING)
  @Column(name = "version_status", nullable = false)
  @Builder.Default
  @Audited
  private DocumentVersionStatus versionStatus = DocumentVersionStatus.CURRENT;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false)
  @Builder.Default
  @Audited
  private DocumentReviewStatus reviewStatus = DocumentReviewStatus.PENDING_REVIEW;

  @Audited private UUID uploadedBy;

  @Column(nullable = false)
  @Audited
  private String uploaderType;

  @Audited private @Nullable UUID reviewedBy;
  @Audited private @Nullable String reviewerType;
  @Audited private @Nullable Instant reviewedAt;

  @Column(length = 2000)
  private @Nullable String observation;

  public boolean isCurrent() {
    return versionStatus == DocumentVersionStatus.CURRENT;
  }

  public void requireMutable() {
    if (!isCurrent() || reviewStatus == DocumentReviewStatus.ACCEPTED) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_VERSION_LOCKED);
    }
  }

  public void supersede() {
    requireMutable();
    versionStatus = DocumentVersionStatus.SUPERSEDED;
  }

  public void withdraw() {
    requireMutable();
    versionStatus = DocumentVersionStatus.WITHDRAWN;
  }

  public void review(
      DocumentReviewStatus status, String note, UUID actorId, String actorType, Instant now) {
    requireMutable();
    if (reviewStatus != DocumentReviewStatus.PENDING_REVIEW
        || status == DocumentReviewStatus.PENDING_REVIEW) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_REVIEW_INVALID);
    }
    if (status == DocumentReviewStatus.OBSERVED && (note == null || note.isBlank())) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_OBSERVATION_REQUIRED);
    }
    reviewStatus = status;
    observation = note == null ? null : note.trim();
    reviewedBy = actorId;
    reviewerType = actorType;
    reviewedAt = now;
  }

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_path", nullable = false, length = 500)
  private String storagePath;

  @Column(name = "content_type", nullable = false, length = 100)
  @Audited
  private String contentType;

  @Column(name = "file_size", nullable = false)
  @Audited
  private Long fileSize;

  public boolean markDeleted(final Instant now) {
    return super.markDeleted(now);
  }

  public String getFilePath() {
    return storagePath;
  }

  // Lombok initializes the entity at build(); builder fields are intentionally incomplete.
  @SuppressWarnings("NullAway.Init")
  public static class EnrollmentAttachmentBuilder {
    public EnrollmentAttachmentBuilder filePath(String filePath) {
      this.storagePath = filePath;

      return this;
    }
  }
}
