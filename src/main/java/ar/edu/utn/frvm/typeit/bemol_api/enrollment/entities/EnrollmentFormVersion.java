package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "enrollment_form_versions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "enrollment_form_versions_institution_id_id_unique",
          columnNames = {"institution_id", "id"}),
      @UniqueConstraint(
          name = "enrollment_form_versions_number_unique",
          columnNames = {"form_id", "version_number"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentFormVersion extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("ENROLLMENT_FORM_VERSION", "Versión de formulario");

  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false, updatable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "form_id", nullable = false, updatable = false)
  private EnrollmentForm form;

  @Column(name = "version_number", nullable = false, updatable = false)
  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EnrollmentFormVersionStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private EnrollmentFormDefinition definition;

  @Version private long revision;

  @Column(name = "published_at")
  private @Nullable Instant publishedAt;

  @Column(name = "archived_at")
  private @Nullable Instant archivedAt;

  public static EnrollmentFormVersion draft(
      final EnrollmentForm form,
      final int versionNumber,
      final EnrollmentFormDefinition definition) {
    if (versionNumber < 1) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.VERSION_NUMBER_INVALID);
    }
    requireDefinition(definition);
    final var version = new EnrollmentFormVersion();
    version.institution = form.getInstitution();
    version.form = form;
    version.versionNumber = versionNumber;
    version.status = EnrollmentFormVersionStatus.DRAFT;
    version.definition = definition;
    return version;
  }

  public void replaceDefinition(
      final EnrollmentFormDefinition definition, final long expectedRevision) {
    requireRevision(expectedRevision);
    requireDraft();
    requireDefinition(definition);
    this.definition = definition;
  }

  public void publish(final Instant publishedAt, final long expectedRevision) {
    requireRevision(expectedRevision);
    requireDraft();
    if (publishedAt == null) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.TRANSITION_DATE_INVALID);
    }
    definition.validateForPublication();

    this.publishedAt = publishedAt;
    status = EnrollmentFormVersionStatus.PUBLISHED;
  }

  public void archive(final Instant archivedAt, final long expectedRevision) {
    requireRevision(expectedRevision);
    if (status != EnrollmentFormVersionStatus.PUBLISHED) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.VERSION_NOT_PUBLISHED);
    }
    if (archivedAt == null || publishedAt == null || archivedAt.isBefore(publishedAt)) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.TRANSITION_DATE_INVALID);
    }

    this.archivedAt = archivedAt;
    status = EnrollmentFormVersionStatus.ARCHIVED;
  }

  public void requireAssignableTo(final UUID institutionId) {
    if (!institution.getId().equals(institutionId)) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.INSTITUTION_MISMATCH);
    }
    if (status != EnrollmentFormVersionStatus.PUBLISHED) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.VERSION_NOT_PUBLISHED);
    }
  }

  private void requireDraft() {
    if (status != EnrollmentFormVersionStatus.DRAFT) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.VERSION_NOT_EDITABLE);
    }
  }

  private void requireRevision(final long expectedRevision) {
    if (revision != expectedRevision) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.REVISION_CONFLICT);
    }
  }

  private static void requireDefinition(final EnrollmentFormDefinition definition) {
    if (definition == null) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.DEFINITION_INVALID);
    }
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .expose("formId", "Identificador del formulario", form.getId())
        .expose("versionId", "Identificador de la versión", id)
        .expose("versionNumber", "Versión", versionNumber)
        .expose("revision", "Revisión", revision)
        .build();
  }
}
