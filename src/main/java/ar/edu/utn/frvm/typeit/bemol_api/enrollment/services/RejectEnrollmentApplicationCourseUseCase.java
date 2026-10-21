package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationCourseResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.RejectEnrollmentApplicationCourseRequest;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RejectEnrollmentApplicationCourseUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;

  private final EnrollmentApplicationCourseRepository applicationCourseRepository;
  private final Clock clock;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;

  @Transactional
  public EnrollmentApplicationCourseResponse execute(
      final UUID institutionId,
      final UUID applicationId,
      final UUID applicationCourseId,
      final RejectEnrollmentApplicationCourseRequest request,
      final @Nullable UUID resolvedByPersonId) {
    accessGuard.require(
        PermissionCode.ENROLLMENT_APPLICATION_COURSE_REJECT,
        institutionId,
        ScopedResource.ENROLLMENT_APPLICATION_COURSE,
        applicationCourseId);

    enrollmentInstitutionLock.lock(institutionId);
    final EnrollmentApplicationCourse selection =
        applicationCourseRepository
            .findByIdAndInstitutionIdForUpdate(applicationCourseId, institutionId)
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.COURSE_APPLICATION_NOT_FOUND));
    if (!selection.getEnrollmentApplication().getId().equals(applicationId)) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_APPLICATION_NOT_FOUND);
    }
    if (!selection.getEnrollmentApplication().isAdmitted()) {
      throw new EnrollmentValidationException(EnrollmentMessages.PARENT_NOT_APPROVED);
    }
    if (request.expectedVersion() != null && request.expectedVersion() != selection.getVersion()) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_VERSION_STALE);
    }
    if (selection.getStatus() != EnrollmentApplicationCourseStatus.PENDING
        && selection.getStatus() != EnrollmentApplicationCourseStatus.WAITLISTED) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.COURSE_APPLICATION_ALREADY_RESOLVED);
    }

    selection.reject("ADMIN_REJECTED", request.reason(), clock.instant(), resolvedByPersonId);
    auditRecorder.recordCurrentActor(
        selection.getEnrollmentApplication().getInstitution(),
        selection.getEnrollmentApplication().getApplicantPerson().getId(),
        AuditAction.ENROLLMENT_APPLICATION_COURSE_REJECTED,
        AuditEntityType.ENROLLMENT_APPLICATION,
        applicationId,
        AuditOrigin.administration(),
        Map.of(
            "after",
            Map.of(
                "courseId",
                selection.getCourse().getId().toString(),
                "status",
                selection.getStatus().name())));
    return EnrollmentApplicationCourseResponse.from(selection);
  }
}
