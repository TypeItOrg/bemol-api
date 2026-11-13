package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.ApplicationNotEditableException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidEnrollmentApplicationStateException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.MissingRejectionReasonException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "enrollment_applications",
    uniqueConstraints =
        @UniqueConstraint(
            name = "enrollment_applications_institution_id_id_unique",
            columnNames = {"institution_id", "enrollment_application_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnrollmentApplication extends SoftDeletable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("ENROLLMENT_APPLICATION", "Inscripción");

  @Id
  @GeneratedUUIDv7
  @Column(name = "enrollment_application_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_person_id", nullable = false)
  private Person applicantPerson;

  /** Who physically submitted the application: the tutor when acting for a dependent. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submitted_by_person_id")
  private @Nullable Person submittedByPerson;

  @Setter(AccessLevel.NONE)
  @Column(name = "training_path_id", nullable = false)
  private UUID trainingPathId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "training_path_id", insertable = false, updatable = false)
  private TrainingPath trainingPath;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academic_year_id")
  private @Nullable AcademicYear academicYear;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "enrollment_period_id")
  private @Nullable EnrollmentPeriod enrollmentPeriod;

  @Embedded @Builder.Default
  private EnrollmentApplicationFormState formState = new EnrollmentApplicationFormState();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private EnrollmentApplicationStatus status;

  @Column(name = "rejection_reason", columnDefinition = "text")
  private @Nullable String rejectionReason;

  @Column(name = "resolved_at")
  private @Nullable Instant resolvedAt;

  @Column(name = "resolved_by_person_id")
  private @Nullable UUID resolvedByPersonId;

  @OneToOne(
      mappedBy = "enrollmentApplication",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  private @Nullable ApplicantEducationBackground educationBackground;

  @OneToOne(
      mappedBy = "enrollmentApplication",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  private ApplicantHealthInclusion healthInclusion;

  @OneToOne(
      mappedBy = "enrollmentApplication",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  private ApplicantResponsible responsible;

  @OneToOne(
      mappedBy = "enrollmentApplication",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  private ApplicantPreference preference;

  @OneToMany(
      mappedBy = "enrollmentApplication",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  @Builder.Default
  private List<EnrollmentAttachment> attachments = new ArrayList<>();

  @OneToMany(mappedBy = "application", cascade = CascadeType.ALL)
  @Builder.Default
  private List<EnrollmentDocumentRequirement> documentRequirements = new ArrayList<>();

  @OneToMany(mappedBy = "application", cascade = CascadeType.ALL)
  @Builder.Default
  private List<EnrollmentDocumentRequest> documentRequests = new ArrayList<>();

  public void addDocumentRequest(final EnrollmentDocumentRequest request) {
    documentRequests.add(request);
  }

  public void addDocumentRequirement(EnrollmentDocumentRequirement requirement) {
    documentRequirements.add(requirement);
  }

  @OneToMany(
      mappedBy = "enrollmentApplication",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  @Builder.Default
  @BatchSize(size = 50)
  private List<EnrollmentApplicationCourse> courseSelections = new ArrayList<>();

  public void setEducationBackground(ApplicantEducationBackground educationBackground) {
    this.educationBackground = educationBackground;

    if (educationBackground != null) {
      educationBackground.setEnrollmentApplication(this);
    }
  }

  public void setHealthInclusion(ApplicantHealthInclusion healthInclusion) {
    this.healthInclusion = healthInclusion;

    if (healthInclusion != null) {
      healthInclusion.setEnrollmentApplication(this);
    }
  }

  public void setResponsible(ApplicantResponsible responsible) {
    this.responsible = responsible;

    if (responsible != null) {
      responsible.setEnrollmentApplication(this);
    }
  }

  public void setPreference(ApplicantPreference preference) {
    this.preference = preference;

    if (preference != null) {
      preference.setEnrollmentApplication(this);
    }
  }

  public void addAttachment(EnrollmentAttachment attachment) {
    attachments.add(attachment);
    attachment.setEnrollmentApplication(this);
  }

  public void addCourseSelection(final EnrollmentApplicationCourse selection) {
    courseSelections.add(selection);
  }

  public void bindFormVersion(final EnrollmentFormVersion version) {
    if (id != null
        || formState.getFormVersion() != null
        || status != EnrollmentApplicationStatus.DRAFT) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.APPLICATION_FORM_IMMUTABLE);
    }

    version.requireAssignableTo(institution.getId());
    formState.bindFormVersion(version);
  }

  public @Nullable AcademicYear commonAcademicYear() {
    if (academicYear != null) {
      return academicYear;
    }
    if (courseSelections.isEmpty()) {
      return null;
    }
    final var year = courseSelections.getFirst().getCourse().getAcademicYear();
    return courseSelections.stream()
            .allMatch(
                selection -> selection.getCourse().getAcademicYear().getId().equals(year.getId()))
        ? year
        : null;
  }

  public void replaceCustomAnswers(final Map<String, Object> answers) {
    if (!isEditable()) {
      throw new ApplicationNotEditableException(id);
    }
    formState.replaceAnswers(answers);
  }

  public void assignSubmitter(final Person submitter) {
    submittedByPerson = submitter;
  }

  public static EnrollmentApplication createForTrainingPath(
      final Institution institution,
      final Person applicantPerson,
      final TrainingPath trainingPath,
      final AcademicYear academicYear) {
    if (academicYear.getStatus() != AcademicYearStatus.ACTIVE
        || !academicYear.getInstitution().getId().equals(institution.getId())
        || !trainingPath.getInstitution().getId().equals(institution.getId())) {
      throw new EnrollmentValidationException(EnrollmentMessages.APPLICATION_ACADEMIC_YEAR_INVALID);
    }

    return createForTrainingPath(institution, applicantPerson, trainingPath, academicYear, null);
  }

  public static EnrollmentApplication createForTrainingPath(
      final Institution institution,
      final Person applicantPerson,
      final TrainingPath trainingPath,
      final AcademicYear academicYear,
      final @Nullable EnrollmentPeriod enrollmentPeriod) {
    return EnrollmentApplication.builder()
        .institution(institution)
        .applicantPerson(applicantPerson)
        .trainingPathId(trainingPath.getId())
        .trainingPath(trainingPath)
        .academicYear(academicYear)
        .enrollmentPeriod(enrollmentPeriod)
        .status(EnrollmentApplicationStatus.DRAFT)
        .build();
  }

  public void submit() {
    if (!isEditable()) {
      throw new InvalidEnrollmentApplicationStateException(
          EnrollmentMessages.APPLICATION_CANNOT_SUBMIT);
    }
    status = EnrollmentApplicationStatus.SUBMITTED;
  }

  public void cancel() {
    if (!isEditable()) {
      throw new ApplicationNotEditableException(id);
    }

    status = EnrollmentApplicationStatus.CANCELLED;
  }

  public boolean isEditable() {
    return status == EnrollmentApplicationStatus.DRAFT && getDeletedAt() == null;
  }

  public boolean isPendingEvaluation() {
    return status == EnrollmentApplicationStatus.SUBMITTED;
  }

  public boolean isAdmitted() {
    return isApproved() || status == EnrollmentApplicationStatus.PROVISIONALLY_APPROVED;
  }

  public @Nullable Boolean getPendingCourses() {
    if (!isAdmitted() || courseSelections.isEmpty()) {
      return null;
    }

    return courseSelections.stream().anyMatch(selection -> selection.isPendingResolution());
  }

  public boolean isApproved() {
    return status == EnrollmentApplicationStatus.APPROVED;
  }

  public boolean isResolved() {
    return status == EnrollmentApplicationStatus.APPROVED
        || status == EnrollmentApplicationStatus.REJECTED
        || status == EnrollmentApplicationStatus.CANCELLED;
  }

  public void approve(final Instant resolvedAt, final @Nullable UUID resolvedByPersonId) {
    if (status != EnrollmentApplicationStatus.PROVISIONALLY_APPROVED) {
      ensurePendingEvaluation();
    }
    status = EnrollmentApplicationStatus.APPROVED;
    this.resolvedAt = resolvedAt;
    this.resolvedByPersonId = resolvedByPersonId;
  }

  public void approveProvisionally() {
    ensurePendingEvaluation();
    status = EnrollmentApplicationStatus.PROVISIONALLY_APPROVED;
  }

  public void reject(
      final @Nullable String rejectionReason,
      final Instant resolvedAt,
      final @Nullable UUID resolvedByPersonId) {
    ensurePendingEvaluation();

    if (rejectionReason == null || rejectionReason.isBlank()) {
      throw new MissingRejectionReasonException();
    }

    status = EnrollmentApplicationStatus.REJECTED;
    this.rejectionReason = rejectionReason;
    this.resolvedAt = resolvedAt;
    this.resolvedByPersonId = resolvedByPersonId;
  }

  private void ensurePendingEvaluation() {
    if (isResolved()) {
      throw new InvalidEnrollmentApplicationStateException(
          EnrollmentMessages.APPLICATION_ALREADY_RESOLVED);
    }

    if (!isPendingEvaluation()) {
      throw new InvalidEnrollmentApplicationStateException(
          EnrollmentMessages.APPLICATION_NOT_PENDING_EVALUATION);
    }
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return EnrollmentApplicationAuditSnapshot.of(this);
  }

  public AuditSnapshot.Snapshot admissionAuditSnapshot() {
    return EnrollmentApplicationAuditSnapshot.admission(this);
  }
}
