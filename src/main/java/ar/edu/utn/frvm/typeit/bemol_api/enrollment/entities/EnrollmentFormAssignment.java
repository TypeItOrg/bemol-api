package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
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
    name = "enrollment_form_assignments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "enrollment_form_assignments_context_unique",
            columnNames = {"institution_id", "training_path_id", "academic_year_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentFormAssignment extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("ENROLLMENT_FORM_ASSIGNMENT", "Asignación de formulario");

  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false, updatable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "training_path_id", nullable = false, updatable = false)
  private TrainingPath trainingPath;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
  private AcademicYear academicYear;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "form_version_id", nullable = false)
  private EnrollmentFormVersion formVersion;

  @Version private long revision;

  public static EnrollmentFormAssignment create(
      final Institution institution,
      final TrainingPath trainingPath,
      final AcademicYear academicYear,
      final EnrollmentFormVersion formVersion) {
    if (!institution.getId().equals(trainingPath.getInstitution().getId())
        || !institution.getId().equals(academicYear.getInstitution().getId())) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.INSTITUTION_MISMATCH);
    }
    formVersion.requireAssignableTo(institution.getId());

    final var assignment = new EnrollmentFormAssignment();
    assignment.institution = institution;
    assignment.trainingPath = trainingPath;
    assignment.academicYear = academicYear;
    assignment.formVersion = formVersion;
    return assignment;
  }

  public void assignVersion(final EnrollmentFormVersion formVersion, final long expectedRevision) {
    requireRevision(expectedRevision);
    formVersion.requireAssignableTo(institution.getId());

    this.formVersion = formVersion;
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
        .expose("formId", "Identificador del formulario", formVersion.getForm().getId())
        .expose("versionId", "Identificador de la versión", formVersion.getId())
        .expose("trainingPathId", "Identificador del trayecto", trainingPath.getId())
        .expose("academicYearId", "Identificador del ciclo lectivo", academicYear.getId())
        .expose("revision", "Revisión", revision)
        .build();
  }
}
