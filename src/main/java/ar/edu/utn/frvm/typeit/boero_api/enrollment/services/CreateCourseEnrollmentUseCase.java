package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CreateManualCourseEnrollmentRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollApplicationCourseRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.StudentRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCourseEnrollmentUseCase {
  private final AcademicAccessGuard access;
  private final CourseEnrollmentContext context;
  private final CourseEnrollmentSchedules schedules;
  private final CourseEnrollmentAvailability availability;
  private final AcademicEligibilityService eligibility;
  private final CourseEnrollmentRepository enrollments;
  private final EnrollmentApplicationCourseRepository applications;
  private final StudentRepository students;
  private final CourseEnrollmentJournal journal;
  private final EnrollmentApplicationCourseApprovalService approvals;
  private final AssignPersonSystemRoleUseCase roles;
  private final CourseEnrollmentResponseFactory responses;
  private final Clock clock;

  @Transactional
  public CourseEnrollmentResponse manual(
      final UUID institutionId,
      final CreateManualCourseEnrollmentRequest request,
      final @Nullable UUID authorityPersonId) {
    access.require(
        PermissionCode.COURSE_ENROLLMENT_CREATE,
        institutionId,
        ScopedResource.COURSE,
        request.courseId());

    final var institution = context.lockInstitution(institutionId);
    final var student =
        students
            .findByIdAndInstitution_Id(request.studentId(), institutionId)
            .orElseThrow(
                () -> new EnrollmentValidationException(EnrollmentMessages.PERSON_ID_NOT_FOUND));
    final var course = context.activeCourse(institutionId, request.courseId());
    final var courseClass = context.courseClass(institutionId, course, request.courseClassId());
    final var assignments = schedules.resolve(course, courseClass, request.assignments());
    eligibility.requireEligible(institutionId, student.getPerson().getId(), course);
    availability.requireAvailable(institutionId, student, course, assignments);

    final var enrollment =
        CourseEnrollment.create(
            institution,
            student,
            course,
            courseClass,
            CourseEnrollmentSource.MANUAL,
            null,
            clock.instant());
    enrollments.save(enrollment);
    schedules.persist(institution, enrollment, assignments);
    journal.record(
        institution,
        enrollment,
        null,
        CourseEnrollmentStatus.ENROLLED,
        null,
        AcademicEnrollmentStatus.IN_PROGRESS,
        "MANUAL_ENROLLMENT",
        null,
        authorityPersonId);
    journal.flush();
    approvals.reevaluateApprovedPendingForCourse(institutionId, course.getId());

    return responses.from(enrollment);
  }

  @Transactional
  public CourseEnrollmentResponse fromApplication(
      final UUID institutionId,
      final UUID applicationId,
      final UUID applicationCourseId,
      final EnrollApplicationCourseRequest request,
      final @Nullable UUID authorityPersonId) {
    access.require(
        PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL,
        institutionId,
        ScopedResource.ENROLLMENT_APPLICATION_COURSE,
        applicationCourseId);

    final var institution = context.lockInstitution(institutionId);
    final var applicationCourse =
        applications
            .findByIdAndInstitutionIdForUpdate(applicationCourseId, institutionId)
            .orElseThrow(EnrollmentApplicationNotFoundException::new);
    if (!applicationCourse.getEnrollmentApplication().getId().equals(applicationId)) {
      throw new EnrollmentApplicationNotFoundException();
    }
    if (!applicationCourse.getEnrollmentApplication().isAdmitted()) {
      throw new EnrollmentValidationException(EnrollmentMessages.PARENT_NOT_APPROVED);
    }
    if (applicationCourse.getStatus() == EnrollmentApplicationCourseStatus.ENROLLED) {
      return responses.from(
          enrollments
              .findByInstitution_IdAndApplicationCourse_Id(institutionId, applicationCourse.getId())
              .orElseThrow(EnrollmentApplicationNotFoundException::new));
    }
    applicationCourse.ensurePendingResolution();
    CourseEnrollmentContext.requireExpectedVersion(
        applicationCourse.getVersion(), request.expectedVersion());

    final var course = context.activeCourse(institutionId, applicationCourse.getCourse().getId());
    final var courseClass = context.courseClass(institutionId, course, request.courseClassId());
    final var assignments = schedules.resolve(course, courseClass, request.assignments());
    final var student =
        context.getOrCreateStudent(
            institution, applicationCourse.getEnrollmentApplication().getApplicantPerson());
    eligibility.requireEligible(institutionId, student.getPerson().getId(), course);
    availability.requireAvailable(institutionId, student, course, assignments);

    final var enrollment =
        CourseEnrollment.create(
            institution,
            student,
            course,
            courseClass,
            CourseEnrollmentSource.APPLICATION,
            applicationCourse.getEnrollmentApplication(),
            clock.instant());
    enrollment.assignApplicationCourse(applicationCourse);
    enrollments.save(enrollment);
    schedules.persist(institution, enrollment, assignments);
    applicationCourse.enroll(clock.instant(), authorityPersonId);
    applications.save(applicationCourse);
    journal.snapshot(institution, applicationCourse, courseClass, assignments, authorityPersonId);
    journal.record(
        institution,
        enrollment,
        null,
        CourseEnrollmentStatus.ENROLLED,
        null,
        AcademicEnrollmentStatus.IN_PROGRESS,
        "APPLICATION_ENROLLMENT",
        null,
        authorityPersonId);
    journal.flush();
    approvals.reevaluateApprovedPendingForCourse(institutionId, course.getId());

    if (student.getPerson() != null) {
      roles.execute(student.getPerson(), SystemRoleCode.STUDENT, true);
    }

    return responses.from(enrollment);
  }
}
