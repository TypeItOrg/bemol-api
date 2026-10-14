package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseWithdrawalType;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateAcademicEnrollmentStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.WithdrawCourseEnrollmentRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
  private final CourseEnrollmentResponseFactory responses;
  private final Clock clock;

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
    CourseEnrollmentContext.requireExpectedVersion(
        enrollment.getVersion(), request.expectedVersion());

    final var previousStatus = enrollment.getStatus();
    final var previousAcademicStatus = enrollment.getAcademicStatus();
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
