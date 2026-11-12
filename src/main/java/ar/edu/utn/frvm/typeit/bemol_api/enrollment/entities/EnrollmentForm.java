package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "enrollment_forms",
    uniqueConstraints =
        @UniqueConstraint(
            name = "enrollment_forms_institution_id_id_unique",
            columnNames = {"institution_id", "id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentForm extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("ENROLLMENT_FORM", "Formulario de inscripción");

  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false, updatable = false)
  private Institution institution;

  @Column(nullable = false, length = 150)
  private String name;

  @Version private long revision;

  public static EnrollmentForm create(final Institution institution, final String name) {
    final var form = new EnrollmentForm();
    form.institution = institution;
    form.rename(name);
    return form;
  }

  public void rename(final String name) {
    if (name == null
        || name.isBlank()
        || name.length() > 150
        || name.codePoints()
            .anyMatch(
                point ->
                    point == 0
                        || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.NAME_INVALID);
    }
    this.name = name.strip();
  }

  public void requireRevision(final long expectedRevision) {
    if (revision != expectedRevision) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.REVISION_CONFLICT);
    }
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .expose("formId", "Identificador del formulario", id)
        .expose("revision", "Revisión", revision)
        .build();
  }
}
