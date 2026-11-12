package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentAudit {
  private final AuditEventRecorder audit;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final CourseEnrollment enrollment,
      final @Nullable CourseEnrollmentStatus previousStatus,
      final @Nullable AcademicEnrollmentStatus previousAcademicStatus) {
    final AuditOperation action;
    if (previousStatus == null) {
      action = EnrollmentAuditAction.COURSE_ENROLLMENT_CREATED;
    } else if (previousStatus != enrollment.getStatus()) {
      action = EnrollmentAuditAction.COURSE_ENROLLMENT_STATUS_CHANGED;
    } else {
      action = EnrollmentAuditAction.COURSE_ENROLLMENT_ACADEMIC_STATUS_CHANGED;
    }
    final var before =
        previousStatus == null
            ? AuditSnapshot.empty()
            : enrollment.auditStatusSnapshot(previousStatus, previousAcademicStatus);
    audit.recordSnapshotChange(
        enrollment.getInstitution(),
        enrollment.getStudent().getPerson().getId(),
        action,
        CourseEnrollment.AUDIT_TYPE,
        enrollment.getId(),
        "Cursada",
        before,
        enrollment.auditSnapshot());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordReasonChange(
      final CourseEnrollment enrollment, final @Nullable String previousReason) {
    audit.recordSnapshotChange(
        enrollment.getInstitution(),
        enrollment.getStudent().getPerson().getId(),
        EnrollmentAuditAction.COURSE_ENROLLMENT_ACADEMIC_STATUS_CHANGED,
        CourseEnrollment.AUDIT_TYPE,
        enrollment.getId(),
        "Cursada",
        enrollment.auditReasonSnapshot(previousReason),
        enrollment.auditReasonSnapshot(enrollment.getActionReason()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSelection(
      final EnrollmentApplicationCourse selection,
      final EnrollmentApplicationCourseStatus previousStatus,
      final boolean removed) {
    final var application = selection.getEnrollmentApplication();
    final AuditOperation action;
    if (removed) {
      action = EnrollmentAuditAction.ENROLLMENT_APPLICATION_DRAFT_UPDATED;
    } else if (selection.getStatus() == EnrollmentApplicationCourseStatus.WAITLISTED) {
      action = EnrollmentAuditAction.ENROLLMENT_APPLICATION_COURSE_WAITLISTED;
    } else {
      action = EnrollmentAuditAction.ENROLLMENT_APPLICATION_COURSE_REJECTED;
    }
    audit.recordSnapshotChange(
        application.getInstitution(),
        application.getApplicantPerson().getId(),
        action,
        EnrollmentApplication.AUDIT_TYPE,
        application.getId(),
        "Selección de curso",
        selection.auditSnapshot(previousStatus, false),
        selection.auditSnapshot(selection.getStatus(), removed));
  }
}
