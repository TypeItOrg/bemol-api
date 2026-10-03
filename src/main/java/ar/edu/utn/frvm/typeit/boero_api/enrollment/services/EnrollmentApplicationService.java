package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantResponsible;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.ApplicationNotEditableException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentPeriodClosedException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseSelectionDto;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentApplicationService {
  private final EnrollmentApplicationRepository applications;
  private final EnrollmentApplicationPeriodService periods;
  private final EnrollmentApplicationResponseFactory responses;
  private final PersonRepository people;
  private final TrainingPathRepository paths;
  private final EnrollmentDraftUpdater drafts;
  private final EnrollmentCourseSelectionService courses;
  private final EnrollmentSubmissionValidator submissionValidator;
  private final EnrollmentApplicationCourseApprovalService approvals;
  private final Clock clock;
  private final EnrollmentInstitutionLock institutionLock;
  private final EnrollmentDocumentRequirementsService documents;
  private final EnrollmentAdmissionHistory history;
  private final QueryEnrollmentApplicationsUseCase queries;
  private final AcademicYearRepository academicYears;
  private final PersonGuardianRepository personGuardianRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public EnrollmentApplicationResponse startOrGetApplication(
      final UUID institutionId,
      final UUID personId,
      final StartEnrollmentApplicationRequest request) {
    // Without applicantPersonId the caller applies for themself. Otherwise they act as a tutor of
    // that person, which must be backed by an existing guardianship link. No application exists
    // yet here, so the link is checked per person pair; afterwards the same person_guardians rows
    // back EnrollmentApplicationRepository.ACCESSIBLE_BY_PERSON.
    final UUID applicantPersonId =
        request.getApplicantPersonId() != null ? request.getApplicantPersonId() : personId;
    final boolean actingForDependent = !applicantPersonId.equals(personId);

    if (!actingForDependent
        && personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
            personId, institutionId, SystemRoleCode.GUARDIAN.name())) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.ENROLLMENT_APPLICATION_GUARDIAN_MUST_USE_DEPENDENT);
    }

    if (actingForDependent
        && !personGuardianRepository
            .existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
                institutionId, personId, applicantPersonId, List.of(GuardianLinkStatus.ACTIVE))) {
      throw new UnauthorizedGuardianshipException();
    }

    institutionLock.lock(institutionId);
    final var person =
        people
            .findByIdAndInstitution_Id(applicantPersonId, institutionId)
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.ENROLLMENT_APPLICATION_APPLICANT_REQUIRED));
    if (request.getTrainingPathId() == null) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_SELECTION_REQUIRED);
    }
    final var path =
        paths
            .findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
                requireNonNull(request.getTrainingPathId()), institutionId)
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.ENROLLMENT_APPLICATION_TRAINING_PATH_INVALID));
    final var activeYears =
        academicYears.findAllByInstitutionIdAndStatus(institutionId, AcademicYearStatus.ACTIVE);
    if (activeYears.size() != 1) {
      throw new EnrollmentValidationException(EnrollmentMessages.APPLICATION_ACADEMIC_YEAR_INVALID);
    }

    final var academicYear = activeYears.getFirst();
    if (request.getAcademicYearId() != null
        && !request.getAcademicYearId().equals(academicYear.getId())) {
      throw new EnrollmentValidationException(EnrollmentMessages.APPLICATION_ACADEMIC_YEAR_INVALID);
    }

    if (!periods.hasOpenCourses(institutionId, path.getId(), academicYear.getId())) {
      throw new EnrollmentPeriodClosedException();
    }

    final var existing =
        applications.findDraftsForTrainingPathAndAcademicYear(
            institutionId, person.getId(), path.getId(), academicYear.getId(), Pageable.ofSize(1));
    if (!existing.isEmpty()) {
      return responses.from(existing.getFirst());
    }

    final var application =
        EnrollmentApplication.createForTrainingPath(
            path.getInstitution(), person, path, academicYear);
    if (personId.equals(person.getId())) {
      application.assignSubmitter(person);
    } else {
      final var submitter =
          people
              .findByIdAndInstitution_Id(personId, institutionId)
              .orElseThrow(
                  () ->
                      new EnrollmentValidationException(
                          EnrollmentMessages.ENROLLMENT_APPLICATION_APPLICANT_REQUIRED));
      application.assignSubmitter(submitter);
      application.setResponsible(responsibleFrom(submitter));
    }
    documents.snapshot(application);
    saveDraft(application);
    history.record(application);
    audit(personId, application, AuditAction.ENROLLMENT_APPLICATION_STARTED);

    return responses.from(application);
  }

  private ApplicantResponsible responsibleFrom(final Person tutor) {
    return ApplicantResponsible.builder()
        .fullName(tutor.getFirstName() + " " + tutor.getLastName())
        .documentNumber(tutor.getDocumentNumber())
        .phoneNumber(tutor.getPhoneNumber())
        .email(tutor.getEmail())
        .build();
  }

  @Transactional
  public EnrollmentApplicationResponse updateDraft(
      final UUID personId, final UUID applicationId, final UpdateEnrollmentDraftRequest request) {
    final var application = lockOwnedApplication(personId, applicationId);
    requireOpenDraft(application);

    if (request.getData() != null) {
      drafts.update(application, request.getData());
    }
    final var saved = saveDraft(application);
    audit(personId, saved, AuditAction.ENROLLMENT_APPLICATION_DRAFT_UPDATED);

    return responses.from(saved);
  }

  @Transactional
  public EnrollmentApplicationResponse cancelApplication(
      final UUID personId, final UUID applicationId) {
    final var application = lockOwnedApplication(personId, applicationId);
    application.cancel();
    history.record(application);
    for (final var selection : application.getCourseSelections()) {
      if (selection.isPendingResolution()) {
        selection.cancel(clock.instant(), personId);
      }
    }
    final var saved = applications.save(application);
    audit(personId, saved, AuditAction.ENROLLMENT_APPLICATION_CANCELLED);

    return responses.from(saved);
  }

  @Transactional
  public EnrollmentApplicationResponse submitApplication(
      final UUID personId, final UUID applicationId) {
    final var application = lockOwnedApplication(personId, applicationId);
    requireOpenDraft(application);
    submissionValidator.validate(application, personId);

    if (application.getCourseSelections() != null && !application.getCourseSelections().isEmpty()) {
      courses.update(
          application,
          application.getCourseSelections().stream()
              .map(
                  selection ->
                      new CourseSelectionDto(
                          selection.getCourse().getId(),
                          selection.getPreferredTeacher() == null
                              ? null
                              : selection.getPreferredTeacher().getId()))
              .toList());
      approvals.markSubmitted(application);
    }
    documents.synchronize(application, clock.instant());
    documents.requireSubmission(application);
    application.submit();
    history.record(application);

    final EnrollmentApplication saved;
    try {
      saved = applications.saveAndFlush(application);
    } catch (DataIntegrityViolationException exception) {
      throw EnrollmentIntegrityViolationTranslator.submission(exception);
    }
    audit(personId, saved, AuditAction.ENROLLMENT_APPLICATION_SUBMITTED);

    return responses.from(saved);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<EnrollmentApplicationResponse> listApplications(
      final UUID institutionId,
      final @Nullable UUID periodId,
      final @Nullable EnrollmentApplicationStatus status,
      final @Nullable String search,
      final Pageable pageable) {
    return queries.list(institutionId, periodId, status, search, pageable);
  }

  @Transactional(readOnly = true)
  public EnrollmentApplicationResponse getApplicationById(
      final UUID personId, final UUID applicationId) {
    return queries.get(null, personId, applicationId);
  }

  @Transactional(readOnly = true)
  public EnrollmentApplicationResponse getApplicationById(
      final @Nullable UUID institutionId, final @Nullable UUID personId, final UUID applicationId) {
    return queries.get(institutionId, personId, applicationId);
  }

  private EnrollmentApplication lockOwnedApplication(
      final UUID personId, final UUID applicationId) {
    final var institutionId =
        applications
            .findOwnedInstitutionId(applicationId, personId)
            .orElseThrow(() -> new EnrollmentApplicationNotFoundException(applicationId));
    institutionLock.lock(institutionId);

    return applications
        .findAccessibleForUpdate(applicationId, personId)
        .orElseThrow(() -> new EnrollmentApplicationNotFoundException(applicationId));
  }

  private void requireOpenDraft(final EnrollmentApplication application) {
    periods.requireOpen(application);
    if (application.getStatus() != EnrollmentApplicationStatus.DRAFT) {
      throw new ApplicationNotEditableException(application.getId());
    }
  }

  /** The subject is always the applicant; the actor may be the applicant or their tutor. */
  private void audit(
      final UUID actorPersonId, final EnrollmentApplication application, final AuditAction action) {
    auditEventRecorder.record(
        application.getInstitution(),
        actorPersonId,
        application.getApplicantPerson().getId(),
        action,
        AuditEntityType.ENROLLMENT_APPLICATION,
        application.getId());
  }

  private EnrollmentApplication saveDraft(final EnrollmentApplication application) {
    try {
      return applications.saveAndFlush(application);
    } catch (DataIntegrityViolationException exception) {
      throw EnrollmentIntegrityViolationTranslator.draft(exception);
    }
  }
}
