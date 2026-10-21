package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import java.util.LinkedHashMap;
import java.util.Map;
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
    final AuditAction action;
    if (previousStatus == null) {
      action = AuditAction.COURSE_ENROLLMENT_CREATED;
    } else if (previousStatus != enrollment.getStatus()) {
      action = AuditAction.COURSE_ENROLLMENT_STATUS_CHANGED;
    } else {
      action = AuditAction.COURSE_ENROLLMENT_ACADEMIC_STATUS_CHANGED;
    }
    final Map<String, Object> before = new LinkedHashMap<>();
    if (previousStatus != null) {
      before.put("status", previousStatus.name());
      before.put("courseId", enrollment.getCourse().getId().toString());
      before.put(
          "academicStatus", previousAcademicStatus == null ? null : previousAcademicStatus.name());
    }
    audit.recordChange(
        enrollment.getInstitution(),
        enrollment.getStudent().getPerson().getId(),
        action,
        AuditEntityType.COURSE_ENROLLMENT,
        enrollment.getId(),
        "Cursada",
        before,
        Map.of(
            "status",
            enrollment.getStatus().name(),
            "academicStatus",
            enrollment.getAcademicStatus().name(),
            "courseId",
            enrollment.getCourse().getId().toString()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordReasonChange(
      final CourseEnrollment enrollment, final @Nullable String previousReason) {
    final Map<String, Object> before = new LinkedHashMap<>();
    before.put("actionReason", previousReason);
    final Map<String, Object> after = new LinkedHashMap<>();
    after.put("actionReason", enrollment.getActionReason());
    audit.recordChange(
        enrollment.getInstitution(),
        enrollment.getStudent().getPerson().getId(),
        AuditAction.COURSE_ENROLLMENT_ACADEMIC_STATUS_CHANGED,
        AuditEntityType.COURSE_ENROLLMENT,
        enrollment.getId(),
        "Cursada",
        before,
        after);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSelection(
      final EnrollmentApplicationCourse selection,
      final EnrollmentApplicationCourseStatus previousStatus,
      final boolean removed) {
    final var application = selection.getEnrollmentApplication();
    final var courseId = selection.getCourse().getId().toString();
    final var action =
        removed
            ? AuditAction.ENROLLMENT_APPLICATION_DRAFT_UPDATED
            : selection.getStatus() == EnrollmentApplicationCourseStatus.WAITLISTED
                ? AuditAction.ENROLLMENT_APPLICATION_COURSE_WAITLISTED
                : AuditAction.ENROLLMENT_APPLICATION_COURSE_REJECTED;
    audit.recordChange(
        application.getInstitution(),
        application.getApplicantPerson().getId(),
        action,
        AuditEntityType.ENROLLMENT_APPLICATION,
        application.getId(),
        "Selección de curso",
        Map.of("courseId", courseId, "status", previousStatus.name(), "deleted", false),
        Map.of("courseId", courseId, "status", selection.getStatus().name(), "deleted", removed));
  }
}
