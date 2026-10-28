package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseWithdrawalType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateAcademicEnrollmentStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.WithdrawCourseEnrollmentRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeCourseEnrollmentStatusUseCase {
  private final AcademicAccessGuard access;
  private final CourseEnrollmentContext context;
  private final CourseEnrollmentRepository enrollments;
  private final CourseEnrollmentSchedules schedules;
  private final CourseEnrollmentJournal journal;
  private final CourseEnrollmentAudit audit;
  private final CourseEnrollmentResponseFactory responses;
  private final Clock clock;
  private final CourseClassTeacherRepository teachers;
  private final PersonRoleAssignmentRepository roles;

  @Transactional
  public CourseEnrollmentResponse withdraw(
      final UUID institutionId,
      final UUID enrollmentId,
      final WithdrawCourseEnrollmentRequest request,
      final @Nullable UUID authorityPersonId) {
    access.require(
        PermissionCode.COURSE_ENROLLMENT_WITHDRAW,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    final var institution = context.lockInstitution(institutionId);
    final var enrollment = lockEnrollment(institutionId, enrollmentId);
    CourseEnrollmentContext.requireExpectedVersion(
        enrollment.getVersion(), request.expectedVersion());
    if (enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND);
    }

    final var target =
        request.type() == CourseWithdrawalType.VOLUNTARY
            ? CourseEnrollmentStatus.WITHDRAWN
            : CourseEnrollmentStatus.ADMINISTRATIVELY_WITHDRAWN;
    final var previousStatus = enrollment.getStatus();
    final var previousAcademicStatus = enrollment.getAcademicStatus();
    enrollment.withdraw(target, clock.instant(), authorityPersonId, request.reason());
    schedules.release(enrollment);
    journal.record(
        institution,
        enrollment,
        previousStatus,
        target,
        previousAcademicStatus,
        enrollment.getAcademicStatus(),
        target.name(),
        request.reason(),
        authorityPersonId);
    journal.flush();

    return responses.from(enrollment);
  }

  @Transactional
  public CourseEnrollmentResponse academicStatus(
      final UUID institutionId,
      final UUID enrollmentId,
      final UpdateAcademicEnrollmentStatusRequest request,
      final @Nullable UUID authorityPersonId) {
    access.require(
        PermissionCode.COURSE_ENROLLMENT_ACADEMIC_STATUS_UPDATE,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    final var institution = context.lockInstitution(institutionId);
    final var enrollment = lockEnrollment(institutionId, enrollmentId);
    return recordAcademicStatus(institution, enrollment, request, authorityPersonId);
  }

  @Transactional
  public CourseEnrollmentResponse academicStatusForTeacher(
      final UUID institutionId,
      final UUID classId,
      final UUID enrollmentId,
      final UpdateAcademicEnrollmentStatusRequest request,
      final UUID teacherId) {
    final var institution = context.lockInstitution(institutionId);
    final var enrollment = lockEnrollment(institutionId, enrollmentId);
    if (!enrollment.getCourseClass().getId().equals(classId)
        || !roles.existsByPerson_IdAndInstitution_IdAndRole_Code(
            teacherId, institutionId, SystemRoleCode.TEACHER.name())
        || !teachers.existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
            institutionId, teacherId, classId)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    return recordAcademicStatus(institution, enrollment, request, teacherId);
  }

  private CourseEnrollmentResponse recordAcademicStatus(
      final ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution institution,
      final CourseEnrollment enrollment,
      final UpdateAcademicEnrollmentStatusRequest request,
      final @Nullable UUID authorityPersonId) {
    CourseEnrollmentContext.requireExpectedVersion(
        enrollment.getVersion(), request.expectedVersion());

    final var previousStatus = enrollment.getStatus();
    final var previousAcademicStatus = enrollment.getAcademicStatus();
    final var previousReason = enrollment.getActionReason();
    enrollment.recordAcademicResult(request.status(), clock.instant(), request.reason());
    if (previousStatus == CourseEnrollmentStatus.ENROLLED) {
      schedules.release(enrollment);
    }
    journal.record(
        institution,
        enrollment,
        previousStatus,
        enrollment.getStatus(),
        previousAcademicStatus,
        request.status(),
        "ACADEMIC_STATUS_UPDATE",
        request.reason(),
        authorityPersonId);
    if (previousStatus == enrollment.getStatus()
        && previousAcademicStatus == enrollment.getAcademicStatus()) {
      audit.recordReasonChange(enrollment, previousReason);
    }
    journal.flush();

    return responses.from(enrollment);
  }

  private CourseEnrollment lockEnrollment(final UUID institutionId, final UUID enrollmentId) {
    return enrollments
        .findByIdAndInstitutionIdForUpdate(enrollmentId, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND));
  }
}
