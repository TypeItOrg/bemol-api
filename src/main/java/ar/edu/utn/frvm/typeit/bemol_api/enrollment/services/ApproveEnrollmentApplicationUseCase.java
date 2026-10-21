package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidEnrollmentApplicationStateException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.StudentRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApproveEnrollmentApplicationUseCase {
  private final EnrollmentApplicationResponseFactory responseFactory;
  private final AcademicAccessGuard accessGuard;

  private final EnrollmentApplicationRepository enrollmentApplicationRepository;
  private final StudentRepository studentRepository;
  private final PersonRepository personRepository;
  private final Clock clock;
  private final BusinessDateProvider businessDateProvider;
  private final EnrollmentApplicationCourseApprovalService applicationCourseApprovalService;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;
  private final EnrollmentDocumentRequirementsService documents;
  private final EnrollmentDocumentAudit audit;
  private final EnrollmentAdmissionHistory history;

  @Transactional
  public EnrollmentApplicationResponse execute(
      final UUID institutionId, final UUID applicationId, final @Nullable UUID resolvedByPersonId) {
    return approve(institutionId, applicationId, resolvedByPersonId, false);
  }

  @Transactional
  public EnrollmentApplicationResponse executeProvisionally(
      UUID institutionId, UUID applicationId, @Nullable UUID resolvedByPersonId) {
    return approve(institutionId, applicationId, resolvedByPersonId, true);
  }

  private EnrollmentApplicationResponse approve(
      UUID institutionId,
      UUID applicationId,
      @Nullable UUID resolvedByPersonId,
      boolean provisional) {
    accessGuard.require(
        PermissionCode.ENROLLMENT_APPLICATION_APPROVE,
        institutionId,
        ScopedResource.ENROLLMENT_APPLICATION,
        applicationId);

    enrollmentInstitutionLock.lock(institutionId);
    final var currentStatus =
        enrollmentApplicationRepository.findStatusByInstitutionIdAndId(
            institutionId, applicationId);
    if (currentStatus.isPresent()
        && (currentStatus.get() == EnrollmentApplicationStatus.APPROVED
            || currentStatus.get() == EnrollmentApplicationStatus.REJECTED
            || currentStatus.get() == EnrollmentApplicationStatus.CANCELLED)) {
      throw new InvalidEnrollmentApplicationStateException(
          EnrollmentMessages.APPLICATION_ALREADY_RESOLVED);
    }
    final var application =
        enrollmentApplicationRepository
            .findByIdAndInstitutionIdForUpdate(institutionId, applicationId)
            .orElseThrow(EnrollmentApplicationNotFoundException::new);

    personRepository
        .findByIdAndInstitutionIdForUpdate(application.getApplicantPerson().getId(), institutionId)
        .orElseThrow(EnrollmentApplicationNotFoundException::new);
    documents.requireApproval(application, provisional);
    final boolean alreadyAdmitted = application.isAdmitted();
    if (provisional) {
      application.approveProvisionally();
    } else {
      application.approve(clock.instant(), resolvedByPersonId);
    }
    history.record(application);
    audit.record(
        EnrollmentDocumentAudit.Actor.from(SecurityContextHolder.getContext().getAuthentication()),
        institutionId,
        applicationId,
        null,
        provisional
            ? EnrollmentDocumentAction.PROVISIONAL_APPROVAL
            : EnrollmentDocumentAction.FINAL_APPROVAL,
        "SUCCESS");

    if (!alreadyAdmitted && application.getStudyPlan() == null) {
      applicationCourseApprovalService.process(application);
    }

    // New course-based applications create Student only with the first effective enrollment.
    // The legacy plan-based flow retains its historical behavior until old clients migrate.
    final boolean legacyApplication =
        application.getStudyPlan() != null || application.hasLegacyStudyPlan();
    if (!alreadyAdmitted
        && legacyApplication
        && !studentRepository.existsByInstitution_IdAndPerson_Id(
            institutionId, application.getApplicantPerson().getId())) {
      studentRepository.save(
          Student.builder()
              .institution(application.getInstitution())
              .person(application.getApplicantPerson())
              .fileNumber(generateFileNumber())
              .enrollmentDate(businessDateProvider.today())
              .build());
    }

    return responseFactory.from(application);
  }

  private String generateFileNumber() {
    final int year = businessDateProvider.today().getYear();
    final long sequence = studentRepository.nextFileNumberSequenceValue();

    return String.format("%d-%05d", year, sequence);
  }
}
